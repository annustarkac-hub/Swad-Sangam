package com.swadsangam.restaurant.ui.cart

import androidx.compose.runtime.setValue
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.PriceStyle
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.BottomActionBar
import com.swadsangam.restaurant.ui.components.AppTextField
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Call
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Spacing
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
    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmClear) com.swadsangam.restaurant.ui.components.ConfirmDialog(stringResource(R.string.clear_cart_q), stringResource(R.string.clear_cart),
        stringResource(R.string.cancel), onConfirm = { vm.clear() }, onDismiss = { confirmClear = false }, danger = true)
    val s by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (s.lines.isEmpty()) {
        EmptyState(stringResource(R.string.cart_empty), stringResource(R.string.cart_empty_body), stringResource(R.string.browse_menu), onBrowse)
        return
    }
    val info = s.info
    val serverCanOrder by vm.serverCanOrder.collectAsStateWithLifecycle()
    // Server decides (it also knows opening hours / temporary closure); the local check is the offline fallback
    val orderingOpen = serverCanOrder ?: (info != null && info.acceptingOrders && info.anyOrderTypeEnabled)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Spacing.s)) {
            if (s.hasUnavailable) {
                item {
                    InfoBanner(stringResource(R.string.cart_has_unavailable), isError = true,
                        action = stringResource(R.string.remove), onAction = { vm.removeUnavailable() })
                }
            }
            item {
                AppCard(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s), padding = PaddingValues(vertical = Spacing.s)) {
                    Text(stringResource(R.string.your_cart_items), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
                    s.lines.forEachIndexed { idx, line ->
                        if (idx > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    line.item?.let { FoodTypeMark(it.foodType, IconSize.xs); Spacer(Modifier.width(Spacing.s)) }
                                    Text(line.item?.displayName(hindi) ?: stringResource(R.string.removed_item), style = MaterialTheme.typography.titleSmall)
                                }
                                if (line.selectedOptions.isNotEmpty()) {
                                    Text(line.selectedOptions.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                }
                                Text(Money.format(line.lineTotalPaise), style = PriceStyle)
                                if (!line.isOrderable) Text(stringResource(R.string.currently_unavailable), color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            QuantityStepper(line.quantity, { q -> if (q <= 0) vm.remove(line) else vm.setQuantity(line, q) }, info?.maxItemQuantity ?: 20)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppButton("+ " + stringResource(R.string.add_more_items), onBrowse, Modifier.padding(horizontal = Spacing.xs).weight(1f, fill = false), variant = ButtonVariant.Text)
                        Spacer(Modifier.weight(1f))
                        AppButton(stringResource(R.string.clear_cart), { confirmClear = true }, variant = ButtonVariant.Text)
                    }
                }
            }
            if (info != null && info.enabledOrderTypes.size > 1) {
                item {
                    AppCard(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
                        Text(stringResource(R.string.order_type), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            info.enabledOrderTypes.forEach { t ->
                                FilterChip(s.orderType == t, { vm.setOrderType(t) }, label = { Text(orderTypeLabel(t)) }, modifier = Modifier.heightIn(min = Size.touch))
                            }
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
                    Text(stringResource(R.string.coupon_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val err = s.quote?.couponError?.let { stringResource(if (it == "COUPON_MIN_ORDER") R.string.coupon_min_order else R.string.coupon_invalid) }
                        AppTextField(s.couponInput, vm::setCouponInput, stringResource(R.string.coupon_code), Modifier.weight(1f), error = err)
                        Spacer(Modifier.width(Spacing.s))
                        if (s.appliedCoupon.isNotEmpty() && s.appliedCoupon == s.couponInput) {
                            AppButton(stringResource(R.string.remove), { vm.removeCoupon() }, variant = ButtonVariant.Text)
                        } else {
                            AppButton(stringResource(R.string.apply), { vm.applyCoupon() }, enabled = s.couponInput.length >= 3, variant = ButtonVariant.Tonal)
                        }
                    }
                }
            }
            item { s.quote?.let { PriceSummary(it, s.appliedCoupon, s.serverQuote, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s)) } }
            if (s.belowMinimum) {
                item { InfoBanner(stringResource(R.string.min_order, Money.format(s.quote!!.minOrderPaise)), isError = true) }
            }
        }
        BottomActionBar {
            if (!orderingOpen) {
                Text(stringResource(R.string.ordering_unavailable_call), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                AppButton(stringResource(R.string.call_to_order), { info?.let { Intents.call(context, it.phone) } }, Modifier.fillMaxWidth(),
                    large = true, icon = Icons.Default.Call)
            } else {
                AppButton(stringResource(R.string.proceed_to_checkout, Money.format(s.quote?.totalPaise ?: 0)), onCheckout,
                    Modifier.fillMaxWidth(), large = true, enabled = s.canCheckout)
            }
        }
    }
}
