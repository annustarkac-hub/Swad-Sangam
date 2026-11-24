package com.swadsangam.restaurant.ui.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.domain.Category
import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.domain.RestaurantInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { DEFAULT, PRICE_LOW_HIGH, PRICE_HIGH_LOW }
enum class FoodFilter { ALL, VEG, NON_VEG }

data class MenuFilters(
    val query: String = "",
    val categoryId: String? = null,
    val food: FoodFilter = FoodFilter.ALL,
    val maxPricePaise: Int? = null,
    val sort: SortOrder = SortOrder.DEFAULT,
)

data class MenuState(
    val categories: List<Category> = emptyList(),
    val groups: List<Pair<Category, List<MenuItem>>> = emptyList(),
    val results: List<MenuItem> = emptyList(),
    val filters: MenuFilters = MenuFilters(),
    val cartQuantities: Map<String, Int> = emptyMap(),
    val info: RestaurantInfo? = null,
    val priceCeilingPaise: Int = 0,
    val loaded: Boolean = false,
    val plainLineKeys: Map<String, String> = emptyMap(),
)

/**
 * Pure, local filtering — runs on the cached menu so search is instant (< 500 ms, FR-04),
 * case-insensitive, and needs no account.
 */
object MenuFilter {
    fun apply(items: List<MenuItem>, cats: List<Category>, f: MenuFilters): List<MenuItem> {
        val q = f.query.trim().lowercase()
        val catNames = cats.associate { it.id to (it.name + " " + (it.nameHi ?: "")).lowercase() }
        var r = items.filter { item ->
            (f.categoryId == null || item.categoryId == f.categoryId) &&
                (f.food == FoodFilter.ALL || (f.food == FoodFilter.VEG) == (item.foodType == FoodType.VEG)) &&
                (f.maxPricePaise == null || item.pricePaise <= f.maxPricePaise) &&
                (q.isEmpty() || item.name.lowercase().contains(q) || (item.nameHi?.lowercase()?.contains(q) == true) ||
                    (catNames[item.categoryId]?.contains(q) == true) ||
                    (q in setOf("veg", "vegetarian", "शाकाहारी") && item.foodType == FoodType.VEG) ||
                    (q in setOf("non veg", "non-veg", "nonveg", "chicken", "मांसाहारी") && item.foodType == FoodType.NON_VEG) ||
                    (item.description?.lowercase()?.contains(q) == true))
        }
        r = when (f.sort) {
            SortOrder.DEFAULT -> r
            SortOrder.PRICE_LOW_HIGH -> r.sortedBy { it.pricePaise }
            SortOrder.PRICE_HIGH_LOW -> r.sortedByDescending { it.pricePaise }
        }
        return r
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class MenuViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val analytics: Analytics,
    private val prefs: com.swadsangam.restaurant.data.prefs.UserPrefs,
) : ViewModel() {
    private val filters = MutableStateFlow(MenuFilters(categoryId = savedState.get<String>("category")?.takeIf { it.isNotBlank() }))

    val pureVeg: StateFlow<Boolean> = prefs.pureVeg.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val refreshing = MutableStateFlow(false)

    val state: StateFlow<MenuState> = combine(menu.categories, combine(menu.items, prefs.pureVeg) { i, v -> if (v) i.filter { it.foodType == com.swadsangam.restaurant.domain.FoodType.VEG } else i },
        filters, cart.lines, menu.restaurant) { cats, items, f, lines, info ->
        val filtered = MenuFilter.apply(items, cats, f)
        MenuState(
            categories = cats,
            groups = if (f.sort == SortOrder.DEFAULT && f.query.isBlank())
                cats.map { c -> c to filtered.filter { it.categoryId == c.id } }.filter { it.second.isNotEmpty() }
            else emptyList(),
            results = filtered,
            filters = f,
            cartQuantities = lines.groupBy { it.itemId }.mapValues { e -> e.value.sumOf { it.quantity } },
            plainLineKeys = lines.filter { it.optionIds.isEmpty() }.associate { it.itemId to it.key },
            info = info,
            priceCeilingPaise = items.maxOfOrNull { it.pricePaise } ?: 0,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MenuState())

    init {
        analytics.track(Analytics.MENU_OPENED)
        filters.debounce(1500).distinctUntilChanged { a, b -> a.query == b.query }
            .filter { it.query.length >= 2 }
            .onEach { analytics.track(Analytics.SEARCH, "length" to it.query.length) }
            .launchIn(viewModelScope)
    }

    fun update(f: (MenuFilters) -> MenuFilters) { filters.value = f(filters.value) }
    fun refresh() = viewModelScope.launch { refreshing.value = true; menu.refresh(); refreshing.value = false }
    fun setPureVeg(on: Boolean) = viewModelScope.launch { prefs.setPureVeg(on) }

    /** Inline stepper on a plain dish (no options): 0 removes it from the cart. */
    fun setQuantity(item: MenuItem, quantity: Int) = viewModelScope.launch {
        val key = state.value.plainLineKeys[item.id] ?: return@launch
        cart.setQuantity(key, quantity, state.value.info?.maxItemQuantity ?: 20)
    }

    fun quickAdd(item: MenuItem) = viewModelScope.launch {
        cart.add(item.id, emptyList(), 1, state.value.info?.maxItemQuantity ?: 20)
        analytics.track(Analytics.ITEM_ADDED, "itemId" to item.id, "source" to "menu")
    }
}
