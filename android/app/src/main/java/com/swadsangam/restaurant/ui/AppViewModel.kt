package com.swadsangam.restaurant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-wide state: cart badge, menu sync, table number from a QR code. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val menu: MenuRepository,
    cart: CartRepository,
    private val orders: com.swadsangam.restaurant.data.repo.OrderRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    /** Active orders (for the "your order" pill above the bottom bar). */
    val activeOrder: StateFlow<com.swadsangam.restaurant.domain.Order?> = orders.myOrders
        .map { l -> l.filter { it.status.isActive }.maxByOrNull { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var watcher: kotlinx.coroutines.Job? = null

    /**
     * While the app is on screen: check active orders every 20 s and show a top-bar notification on
     * every status change (works without Firebase). Started/stopped with the activity.
     */
    fun startOrderWatch() {
        if (watcher?.isActive == true) return
        watcher = viewModelScope.launch {
            while (true) {
                if (orders.hasActiveOrders()) {
                    orders.refreshAllAndCollectChanges().forEach {
                        com.swadsangam.restaurant.notifications.Notifier.showOrderStatus(context, it.token, it.orderNumber, it.status, it.reason, it.type)
                    }
                }
                kotlinx.coroutines.delay(20_000)
            }
        }
    }
    fun stopOrderWatch() { watcher?.cancel(); watcher = null }
    val cartCount: StateFlow<Int> = cart.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    /** Items total for the floating cart bar (final bill is always priced by the server). */
    val cartSubtotal: StateFlow<Int> = cart.lines.map { com.swadsangam.restaurant.domain.CartMath.subtotal(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val sync: StateFlow<SyncState> = menu.sync

    /** Table number from a QR table-ordering link (swadsangam://menu?table=5). */
    val tableNumber = MutableStateFlow<String?>(null)

    /** Splash is held until the cache is ready or 1.5 s pass — never indefinitely (FR-01). */
    val ready = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            launch { kotlinx.coroutines.delay(1500); ready.value = true }
            menu.refresh()
            ready.value = true
        }
    }

    fun refresh() = viewModelScope.launch { menu.refresh() }
}
