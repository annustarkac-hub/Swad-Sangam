package com.swadsangam.restaurant.ui.orders

import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.order.statusTone
import com.swadsangam.restaurant.ui.components.StatusChip
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.order.statusLabel
import com.swadsangam.restaurant.util.TimeFmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orders: OrderRepository,
    private val cart: CartRepository,
    private val menu: MenuRepository,
    private val auth: com.swadsangam.restaurant.data.auth.AuthRepository,
) : ViewModel() {
    val list = orders.myOrders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun refresh() {
        auth.syncOrders { orders.saveFromAccount(it) } // orders from the customer's account (any phone)
        orders.refreshAllAndCollectChanges()
    }

    /** Reorder previous purchases (repeat business). Unavailable dishes are skipped. */
    fun reorder(o: Order, onDone: (added: Int) -> Unit) = viewModelScope.launch {
        val items = menu.itemsNow().associateBy { it.id }
        val max = menu.restaurantNow().maxItemQuantity
        var added = 0
        o.items.forEach { l ->
            val item = items[l.itemId] ?: return@forEach
            if (!item.isAvailable) return@forEach
            val optIds = item.options.filter { it.name in l.options }.map { it.id }
            cart.add(item.id, optIds, l.quantity, max)
            added++
        }
        cart.count.first()
        onDone(added)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOpen: (String) -> Unit, onCart: () -> Unit, onBrowse: () -> Unit,
                 vm: OrdersViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refresh() }

    val l = list ?: return
    if (l.isEmpty()) {
        EmptyState(stringResource(R.string.no_orders), stringResource(R.string.no_orders_body), stringResource(R.string.browse_menu), onBrowse)
        return
    }
    PullToRefreshBox(refreshing, { scope.launch { refreshing = true; vm.refresh(); refreshing = false } }, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item {
                Text(stringResource(R.string.orders_on_device), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
            items(l, key = { it.accessToken }) { o ->
                AppCard(onClick = { onOpen(o.accessToken) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.orderNumber, style = MaterialTheme.typography.titleMedium)
                            Text(TimeFmt.dateTime(o.createdAt), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                        StatusChip(statusLabel(o.status), tone = statusTone(o.status))
                    }
                    Text(o.items.joinToString { "${it.quantity}× ${it.name}" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.s))
                    Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(Money.format(o.totalPaise), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!o.status.isActive) AppButton(stringResource(R.string.reorder), { vm.reorder(o) { onCart() } },
                            variant = ButtonVariant.Tonal, icon = Icons.Default.Replay)
                    }
                }
            }
        }
    }
}
