package com.swadsangam.restaurant.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.components.FoodTypeMark
import com.swadsangam.restaurant.ui.components.InfoBanner
import com.swadsangam.restaurant.ui.components.QuantityStepper
import com.swadsangam.restaurant.util.Intents

@Composable
fun orderTypeLabel(t: OrderType) = stringResource(
    when (t) {
        OrderType.PICKUP -> R.string.pickup
        OrderType.DINE_IN -> R.string.dine_in
        OrderType.DELIVERY -> R.string.delivery
    },
)

@Composable
fun CartScreen(
    hindi: Boolean,
    onBrowse: () -> Unit,
    onCheckout: () -> Unit,
    vm: CartViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (s.lines.isEmpty()) {
        EmptyState(stringResource(R.string.cart_empty), stringResource(R.string.cart_empty_body), stringResource(R.string.browse_menu), onBrowse)
        return
    }
    val info = s.info
    val orderingOpen = info != null && info.acceptingOrders && info.anyOrderTypeEnabled

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (s.hasUnavailable) {
                item {
                    InfoBanner(stringResource(R.string.cart_has_unavailable), isError = true,
                        action = stringResource(R.string.remove), onAction = { vm.removeUnavailable() })
                }
            }
            items(s.lines, key = { it.key }) { line ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            line.item?.let { FoodTypeMark(it.foodType, 14.dp); Spacer(Modifier.width(6.dp)) }
                            Text(line.item?.displayName(hindi) ?: stringResource(R.string.removed_item), style = MaterialTheme.typography.titleSmall)
                        }
                        if (line.selectedOptions.isNotEmpty()) {
                            Text(line.selectedOptions.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${Money.format(line.unitPricePaise)} × ${line.quantity} = ${Money.format(line.lineTotalPaise)}",
                            style = MaterialTheme.typography.bodySmall)
                        if (!line.isOrderable) Text(stringResource(R.string.currently_unavailable), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    QuantityStepper(line.quantity, { vm.setQuantity(line, it) }, info?.maxItemQuantity ?: 20)
                    IconButton({ vm.remove(line) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove)) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            item {
                TextButton(onClick = onBrowse, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.add_more_items)) }
            }
            if (info != null && info.enabledOrderTypes.size > 1) {
                item {
                    Text(stringResource(R.string.order_type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        info.enabledOrderTypes.forEach { t ->
                            FilterChip(s.orderType == t, { vm.setOrderType(t) }, label = { Text(orderTypeLabel(t)) })
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(s.couponInput, vm::setCouponInput, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.coupon_code)) })
                    Spacer(Modifier.width(8.dp))
                    if (s.appliedCoupon.isNotEmpty() && s.appliedCoupon == s.couponInput) {
                        TextButton({ vm.removeCoupon() }) { Text(stringResource(R.string.remove)) }
                    } else {
                        TextButton({ vm.applyCoupon() }, enabled = s.couponInput.length >= 3) { Text(stringResource(R.string.apply)) }
                    }
                }
                s.quote?.couponError?.let {
                    Text(stringResource(if (it == "COUPON_MIN_ORDER") R.string.coupon_min_order else R.string.coupon_invalid),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item { s.quote?.let { PriceSummary(it, s.appliedCoupon, s.serverQuote) } }
            if (s.belowMinimum) {
                item { InfoBanner(stringResource(R.string.min_order, Money.format(s.quote!!.minOrderPaise)), isError = true) }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp)) {
            if (!orderingOpen) {
                Text(stringResource(R.string.ordering_unavailable_call), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { info?.let { Intents.call(context, it.phone) } }, Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.call_to_order))
                }
            } else {
                Button(onClick = onCheckout, enabled = s.canCheckout, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.proceed_to_checkout, Money.format(s.quote?.totalPaise ?: 0)))
                }
            }
        }
    }
}
