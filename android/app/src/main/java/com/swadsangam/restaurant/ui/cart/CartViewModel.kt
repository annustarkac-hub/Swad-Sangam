package com.swadsangam.restaurant.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.CartMath
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.Quote
import com.swadsangam.restaurant.domain.RestaurantInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Choices carried from the cart to checkout. */
@Singleton
class CheckoutDraft @Inject constructor() {
    val orderType = MutableStateFlow<OrderType?>(null)
    val couponCode = MutableStateFlow("")
}

data class CartState(
    val lines: List<CartLine> = emptyList(),
    val info: RestaurantInfo? = null,
    val orderType: OrderType = OrderType.PICKUP,
    val quote: Quote? = null,
    val serverQuote: Boolean = false,
    val couponInput: String = "",
    val appliedCoupon: String = "",
) {
    val hasUnavailable get() = lines.any { !it.isOrderable }
    val belowMinimum get() = quote != null && quote.subtotalPaise < quote.minOrderPaise
    val canCheckout get() = lines.isNotEmpty() && !hasUnavailable && !belowMinimum
}

@OptIn(FlowPreview::class)
@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    menu: MenuRepository,
    private val orders: OrderRepository,
    private val draft: CheckoutDraft,
    private val analytics: Analytics,
) : ViewModel() {
    private val serverQuote = MutableStateFlow<Quote?>(null)
    private val couponInput = MutableStateFlow(draft.couponCode.value)

    val state: StateFlow<CartState> = combine(cart.lines, menu.restaurant, draft.orderType, serverQuote,
        combine(couponInput, draft.couponCode) { a, b -> a to b }) { lines, info, type, sq, (input, applied) ->
        val t = type?.takeIf { it in info.enabledOrderTypes } ?: info.enabledOrderTypes.firstOrNull() ?: OrderType.PICKUP
        CartState(
            lines = lines, info = info, orderType = t,
            quote = sq ?: CartMath.localQuote(lines, info, t),
            serverQuote = sq != null,
            couponInput = input, appliedCoupon = applied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CartState())

    init {
        analytics.track(Analytics.CART_VIEWED)
        // Ask the server for authoritative totals whenever the cart changes (when online).
        combine(cart.lines, draft.orderType, draft.couponCode) { l, t, c -> Triple(l, t, c) }
            .debounce(400)
            .onEach { (lines, _, coupon) ->
                serverQuote.value = if (lines.isEmpty()) null
                else orders.quote(lines.filter { it.isOrderable }, state.value.orderType, coupon)
            }
            .launchIn(viewModelScope)
    }

    fun setQuantity(line: CartLine, q: Int) = viewModelScope.launch {
        cart.setQuantity(line.key, q, state.value.info?.maxItemQuantity ?: 20)
    }
    fun remove(line: CartLine) = viewModelScope.launch { cart.remove(line.key) }
    fun removeUnavailable() = viewModelScope.launch { state.value.lines.filterNot { it.isOrderable }.forEach { cart.remove(it.key) } }
    fun clear() = viewModelScope.launch { cart.clear() }
    fun setOrderType(t: OrderType) { draft.orderType.value = t }
    fun setCouponInput(s: String) { couponInput.value = s.uppercase().filter { it.isLetterOrDigit() }.take(20) }
    fun applyCoupon() { draft.couponCode.value = couponInput.value }
    fun removeCoupon() { couponInput.value = ""; draft.couponCode.value = "" }
}
