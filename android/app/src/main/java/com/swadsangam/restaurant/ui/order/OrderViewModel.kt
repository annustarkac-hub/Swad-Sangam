package com.swadsangam.restaurant.ui.order

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.notifications.OrderStatusWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val orders: OrderRepository,
    menu: MenuRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val token: String = checkNotNull(savedState["token"])
    val order: StateFlow<Order?> = orders.observe(token).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val info: StateFlow<RestaurantInfo?> = menu.restaurant.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val offline = MutableStateFlow(false)
    val cancelError = MutableStateFlow<String?>(null)
    val feedbackGiven: StateFlow<Boolean> = orders.observeFeedbackGiven(token).stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private var polling: Job? = null

    init {
        // Background fallback for status notifications when push is unavailable
        OrderStatusWorker.schedule(context)
    }

    /** Poll only while this screen is visible (keeps backend usage low — PRD §9.4). */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                val fresh = orders.refresh(token)
                offline.value = fresh == null
                // Not marked as notified here: the app-wide watcher still shows the top-bar notification
                if (fresh != null && !fresh.status.isActive) break
                delay(15_000)
            }
        }
    }

    fun stopPolling() { polling?.cancel() }

    fun cancel(reason: String) = viewModelScope.launch {
        orders.cancel(token, reason).onFailure { cancelError.value = it.message }
    }
}
