package com.swadsangam.restaurant.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.domain.MenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemState(
    val item: MenuItem? = null,
    val loaded: Boolean = false,
    val quantity: Int = 1,
    val selected: Set<String> = emptySet(),
    val maxQuantity: Int = 20,
    val categoryName: String? = null,
) {
    /** Final price shown before adding to cart (FR-05). */
    val totalPaise: Int get() = item?.let { (it.pricePaise + it.options.filter { o -> o.id in selected }.sumOf { o -> o.pricePaise }) * quantity } ?: 0
}

@HiltViewModel
class ItemViewModel @Inject constructor(
    savedState: SavedStateHandle,
    menu: MenuRepository,
    private val cart: CartRepository,
    private val analytics: Analytics,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    private val qty = MutableStateFlow(1)
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<ItemState> = combine(menu.items, menu.categories, menu.restaurant, qty, selected) { items, cats, info, q, sel ->
        val item = items.firstOrNull { it.id == id }
        ItemState(item, true, q, sel, info.maxItemQuantity, cats.firstOrNull { it.id == item?.categoryId }?.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemState())

    init { analytics.track(Analytics.ITEM_VIEWED, "itemId" to id) }

    fun setQuantity(q: Int) { qty.value = q.coerceIn(1, state.value.maxQuantity) }
    fun toggleOption(optionId: String) {
        selected.value = if (optionId in selected.value) selected.value - optionId else selected.value + optionId
    }

    fun addToCart(onDone: () -> Unit) = viewModelScope.launch {
        val s = state.value
        val item = s.item ?: return@launch
        cart.add(item.id, s.selected.toList(), s.quantity, s.maxQuantity)
        analytics.track(Analytics.ITEM_ADDED, "itemId" to item.id, "source" to "detail")
        onDone()
    }
}
