package com.swadsangam.restaurant.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.core.PhoneValidator
import com.swadsangam.restaurant.data.prefs.UserPrefs
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.CheckoutForm
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.data.repo.PlaceResult
import com.swadsangam.restaurant.domain.CartLine
import com.swadsangam.restaurant.domain.CartMath
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import com.swadsangam.restaurant.domain.Quote
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.ui.cart.CheckoutDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckoutUi(
    val name: String = "",
    val phone: String = "",
    val orderType: OrderType = OrderType.PICKUP,
    val payment: PaymentMethod = PaymentMethod.CASH,
    val tableNumber: String = "",
    val tableCode: String = "",
    val address: String = "",
    val landmark: String = "",
    /** Delivery pin (from the phone's location or a saved address). */
    val pinLat: Double? = null,
    val pinLng: Double? = null,
    val locating: Boolean = false,
    /** Set when the location could not be read (off / denied / no fix). */
    val locationFailed: Boolean = false,
    val instructions: String = "",
    val notes: String = "",
    val termsAccepted: Boolean = false,
    val submitting: Boolean = false,
    val showErrors: Boolean = false,
    val error: CheckoutError? = null,
)

sealed interface CheckoutError {
    data object Network : CheckoutError
    data class PriceChanged(val newTotalPaise: Int) : CheckoutError
    data class Server(val code: String, val message: String) : CheckoutError
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cart: CartRepository,
    private val menu: MenuRepository,
    private val orders: OrderRepository,
    private val prefs: UserPrefs,
    private val draft: CheckoutDraft,
    private val analytics: Analytics,
    private val auth: com.swadsangam.restaurant.data.auth.AuthRepository,
) : ViewModel() {
    /** Saved addresses of a signed-in customer (empty for guests). */
    val addresses = auth.account
    private val _ui = MutableStateFlow(CheckoutUi())
    val ui: StateFlow<CheckoutUi> = _ui.asStateFlow()
    val info: StateFlow<RestaurantInfo?> = menu.restaurant.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lines: StateFlow<List<CartLine>> = cart.lines.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote.asStateFlow()
    /** False while offline: the bill is a local estimate and the coupon is not yet checked by the server. */
    val quoteVerified = MutableStateFlow(false)
    /** Server message when delivery to the chosen location is not possible (e.g. too far). */
    val deliveryNotice = MutableStateFlow<String?>(null)
    val couponCode: StateFlow<String> get() = draft.couponCode
    private val _placed = MutableStateFlow<Order?>(null)
    val placed: StateFlow<Order?> = _placed.asStateFlow()

    fun start(table: String?) = viewModelScope.launch {
        analytics.track(Analytics.CHECKOUT_STARTED)
        val saved = prefs.customer.first()
        val i = menu.restaurantNow()
        val type = draft.orderType.value?.takeIf { it in i.enabledOrderTypes }
            ?: (if (table != null && i.dineInEnabled) OrderType.DINE_IN else i.enabledOrderTypes.firstOrNull() ?: OrderType.PICKUP)
        _ui.update {
            it.copy(name = saved.name, phone = saved.phone, orderType = type,
                payment = i.enabledPaymentMethods.firstOrNull() ?: PaymentMethod.CASH, tableNumber = table.orEmpty())
        }
        requote()
        // Signed-in customer: fill name, number and default address from the account
        auth.reload()
        auth.account.value?.let { acc ->
            val def = acc.addresses.firstOrNull { it.isDefault } ?: acc.addresses.firstOrNull()
            _ui.update {
                it.copy(name = it.name.ifBlank { acc.profile?.fullName.orEmpty() }, phone = it.phone.ifBlank { acc.profile?.phone.orEmpty() },
                    address = it.address.ifBlank { def?.address.orEmpty() }, landmark = it.landmark.ifBlank { def?.landmark.orEmpty() },
                    pinLat = it.pinLat ?: def?.latitude, pinLng = it.pinLng ?: def?.longitude)
            }
            if (_ui.value.pinLat != null) requote()
        }
    }

    private suspend fun requote() {
        val l = cart.lines.first()
        val i = menu.restaurantNow()
        val u = _ui.value
        val server = orders.quote(l, u.orderType, draft.couponCode.value, u.pinLat, u.pinLng)
        if (server?.deliveryError != null) {
            // Delivery not possible here: show why, keep a local bill estimate, place_order will refuse too.
            deliveryNotice.value = server.deliveryError
            quoteVerified.value = false
            _quote.value = CartMath.localQuote(l, i, u.orderType)
            return
        }
        deliveryNotice.value = null
        quoteVerified.value = server != null
        _quote.value = server ?: CartMath.localQuote(l, i, u.orderType)
    }

    fun edit(f: (CheckoutUi) -> CheckoutUi) {
        val before = _ui.value.orderType
        _ui.update { f(it).copy(error = null) }
        if (_ui.value.orderType != before) {
            draft.orderType.value = _ui.value.orderType
            viewModelScope.launch { requote() }
        }
    }

    /** Saved address chosen (or "new address" with null): carry its pin, if it has one. */
    fun chooseAddress(a: com.swadsangam.restaurant.data.remote.AddressDto?) {
        _ui.update { it.copy(address = a?.address.orEmpty(), landmark = a?.landmark.orEmpty(), pinLat = a?.latitude, pinLng = a?.longitude,
            locationFailed = false, error = null) }
        viewModelScope.launch { requote() }
    }

    fun locate(context: android.content.Context) {
        if (_ui.value.locating) return
        _ui.update { it.copy(locating = true, locationFailed = false) }
        viewModelScope.launch {
            val loc = com.swadsangam.restaurant.core.CurrentLocation.get(context.applicationContext)
            _ui.update {
                if (loc == null) it.copy(locating = false, locationFailed = true)
                else it.copy(locating = false, pinLat = Math.round(loc.latitude * 1e6) / 1e6, pinLng = Math.round(loc.longitude * 1e6) / 1e6)
            }
            if (loc != null) requote()
        }
    }

    fun locationDenied() = _ui.update { it.copy(locating = false, locationFailed = true) }

    fun nameError(u: CheckoutUi) = u.name.trim().length !in 2..60
    fun phoneError(u: CheckoutUi) = PhoneValidator.normalize(u.phone) == null
    fun addressError(u: CheckoutUi) = u.orderType == OrderType.DELIVERY && u.address.trim().length < 10
    fun tableError(u: CheckoutUi) = u.orderType == OrderType.DINE_IN && u.tableNumber.isBlank()
    fun tableCodeError(u: CheckoutUi, required: Boolean) = required && u.orderType == OrderType.DINE_IN && !Regex("\\d{4}").matches(u.tableCode)

    fun submit() {
        val u = _ui.value
        if (u.submitting) return  // guards against double taps
        if (nameError(u) || phoneError(u) || addressError(u) || tableError(u) ||
            tableCodeError(u, info.value?.requireTableCode == true) || !u.termsAccepted) {
            _ui.update { it.copy(showErrors = true) }
            return
        }
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val l = cart.lines.first().filter { it.isOrderable }
            val form = CheckoutForm(
                name = u.name, phone = PhoneValidator.normalize(u.phone)!!, orderType = u.orderType,
                paymentMethod = u.payment, tableNumber = u.tableNumber.trim(), tableCode = u.tableCode.trim(), deliveryAddress = u.address.trim(),
                deliveryLandmark = u.landmark.trim().ifBlank { null },
                deliveryLat = u.pinLat, deliveryLng = u.pinLng, pickupInstructions = u.instructions.trim().ifBlank { null },
                notes = u.notes.trim().ifBlank { null }, couponCode = draft.couponCode.value, termsAccepted = u.termsAccepted,
            )
            when (val r = orders.place(form, l, _quote.value?.totalPaise)) {
                is PlaceResult.Success -> {
                    cart.clear()
                    draft.couponCode.value = ""
                    _placed.value = r.order
                }
                is PlaceResult.Rejected -> {
                    if (r.code in setOf("PRICE_CHANGED", "ITEM_UNAVAILABLE", "INVALID_OPTION")) {
                        menu.refresh()   // pull current prices / availability
                        requote()
                    }
                    if (r.code in setOf("ORDERS_CLOSED", "RESTAURANT_CLOSED", "ORDER_TYPE_UNAVAILABLE")) menu.refresh()
                    _ui.update {
                        it.copy(error = if (r.code == "PRICE_CHANGED") CheckoutError.PriceChanged(_quote.value?.totalPaise ?: 0)
                        else CheckoutError.Server(r.code, r.message))
                    }
                }
                PlaceResult.NetworkError -> _ui.update { it.copy(error = CheckoutError.Network) }
                is PlaceResult.Failed -> _ui.update { it.copy(error = CheckoutError.Server("UNKNOWN", r.message)) }
            }
            _ui.update { it.copy(submitting = false) }
        }
    }

    fun dismissError() = _ui.update { it.copy(error = null) }
}
