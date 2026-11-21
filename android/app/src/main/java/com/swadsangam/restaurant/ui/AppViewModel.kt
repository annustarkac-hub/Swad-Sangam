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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-wide state: cart badge, menu sync, table number from a QR code. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val menu: MenuRepository,
    cart: CartRepository,
) : ViewModel() {
    val cartCount: StateFlow<Int> = cart.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
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
