package com.swadsangam.restaurant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.SyncState
import com.swadsangam.restaurant.domain.Category
import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.domain.Offer
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.domain.RestaurantStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val info: RestaurantInfo? = null,
    val status: RestaurantStatus? = null,
    val categories: List<Category> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val popular: List<MenuItem> = emptyList(),
    val budget: List<MenuItem> = emptyList(),
    val breakfastSnacks: List<MenuItem> = emptyList(),
    val veg: List<MenuItem> = emptyList(),
    val nonVeg: List<MenuItem> = emptyList(),
    val thalis: List<MenuItem> = emptyList(),
    val heroItem: MenuItem? = null,
    val sync: SyncState = SyncState.Idle,
    val hasMenu: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        menu.restaurant, menu.categories, menu.items, menu.offers,
        combine(menu.sync, menu.status) { a, b -> a to b },
    ) { info, cats, items, offers, (sync, status) ->
        val available = items.filter { it.isAvailable }
        HomeState(
            info = info,
            status = status,
            categories = cats,
            offers = offers,
            // "Popular" only when owner-approved (is_popular), never assumed (FR-02)
            popular = available.filter { it.isPopular },
            budget = available.filter { it.pricePaise <= info.budgetThresholdPaise }.sortedBy { it.pricePaise },
            breakfastSnacks = available.filter { it.categoryId in setOf("breakfast", "snacks") },
            veg = available.filter { it.foodType == FoodType.VEG }.take(10),
            nonVeg = available.filter { it.foodType == FoodType.NON_VEG }.take(10),
            thalis = available.filter { it.categoryId == "thalis" },
            heroItem = available.firstOrNull { it.categoryId == "thalis" && it.imageUrl != null }
                ?: available.firstOrNull { it.imageUrl != null },
            sync = sync,
            hasMenu = items.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    init { analytics.track(Analytics.HOME_VIEWED) }

    fun refresh() = viewModelScope.launch { menu.refresh() }

    fun quickAdd(item: MenuItem, max: Int) = viewModelScope.launch {
        cart.add(item.id, emptyList(), 1, max)
        analytics.track(Analytics.ITEM_ADDED, "itemId" to item.id, "source" to "home")
    }

    fun track(event: String) = analytics.track(event)
}
