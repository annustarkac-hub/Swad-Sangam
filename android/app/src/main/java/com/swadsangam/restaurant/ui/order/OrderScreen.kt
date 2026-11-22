package com.swadsangam.restaurant.ui.order

import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusChip
import com.swadsangam.restaurant.ui.components.StatusBanner
import com.swadsangam.restaurant.ui.components.SectionDivider
import com.swadsangam.restaurant.ui.components.LoadingState
import com.swadsangam.restaurant.ui.components.KeyValueRow
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.AppTextField
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Call
import com.swadsangam.restaurant.ui.theme.AppTheme
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.Order
import com.swadsangam.restaurant.domain.OrderStatus
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import com.swadsangam.restaurant.ui.cart.orderTypeLabel
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.components.FoodTypeMark
import com.swadsangam.restaurant.ui.components.InfoBanner
import com.swadsangam.restaurant.util.Intents
import com.swadsangam.restaurant.util.TimeFmt
import kotlinx.coroutines.delay

@Composable
fun statusLabel(s: OrderStatus): String = stringResource(
    when (s) {
        OrderStatus.PLACED -> R.string.status_placed
        OrderStatus.ACCEPTED -> R.string.status_accepted
        OrderStatus.PREPARING -> R.string.status_preparing
        OrderStatus.READY -> R.string.status_ready
        OrderStatus.OUT_FOR_DELIVERY -> R.string.status_out_for_delivery
        OrderStatus.COMPLETED -> R.string.status_completed
        OrderStatus.REJECTED -> R.string.status_rejected
        OrderStatus.CANCELLED -> R.string.status_cancelled
        OrderStatus.UNKNOWN -> R.string.status_unknown
    },
)

@Composable
fun paymentStatusLabel(s: String): String = stringResource(
    when (s) {
        "PAID" -> R.string.pay_paid
        "FAILED" -> R.string.pay_failed
        "REFUND_PENDING" -> R.string.pay_refund_pending
        "REFUNDED" -> R.string.pay_refunded
        else -> R.string.pay_pending
    },
)

@Composable
fun OrderScreen(onFeedback: (String) -> Unit, onHome: () -> Unit, vm: OrderViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val order by vm.order.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val cancelError by vm.cancelError.collectAsStateWithLifecycle()
    val feedbackGiven by vm.feedbackGiven.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.startPolling()
            if (e == Lifecycle.Event.ON_PAUSE) vm.stopPolling()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); vm.stopPolling() }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val o = order ?: run { LoadingState(stringResource(R.string.loading_order)); return }
    val phone = info?.phone ?: o.restaurantPhone

    val failed = o.status in setOf(OrderStatus.REJECTED, OrderStatus.CANCELLED)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.order_number, o.orderNumber), style = MaterialTheme.typography.headlineSmall)
            Text("${orderTypeLabel(o.orderType)}${o.tableNumber?.let { " · " + stringResource(R.string.table_n, it) } ?: ""} · ${TimeFmt.dateTime(o.createdAt)}",
                style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
        if (offline) StatusBanner(stringResource(R.string.status_offline), tone = Tone.Warning, icon = Icons.Default.CloudOff)

        // Status + timeline
        AppCard {
            StatusChip(statusLabel(o.status), tone = statusTone(o.status))
            Spacer(Modifier.height(Spacing.s))
            // Never show "accepted" until the restaurant actually accepts (FR-09)
            if (o.status == OrderStatus.PLACED) {
                Text(stringResource(R.string.awaiting_confirmation), style = MaterialTheme.typography.bodyMedium)
                if (TimeFmt.minutesSince(o.createdAt, now) >= 2) {
                    Spacer(Modifier.height(Spacing.s))
                    StatusBanner(stringResource(R.string.not_confirmed_call), tone = Tone.Warning, action = stringResource(R.string.call),
                        onAction = { Intents.call(context, phone) })
                }
            }
            o.rejectionReason?.let { Text(stringResource(R.string.reason_prefix, it), style = MaterialTheme.typography.bodyMedium) }
            o.cancelReason?.let { Text(stringResource(R.string.reason_prefix, it), style = MaterialTheme.typography.bodyMedium) }
            if (o.status.isActive && o.status != OrderStatus.PLACED) {
                o.estimatedReadyAt?.let { Text(stringResource(R.string.estimated_ready, TimeFmt.time(it)), style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.height(Spacing.m))
            Timeline(o)
        }

        if (o.status == OrderStatus.READY && o.orderType == OrderType.PICKUP) {
            StatusBanner(stringResource(R.string.ready_collect, info?.address ?: ""), tone = Tone.Success, icon = Icons.Default.CheckCircle)
        }

        // Pay at restaurant via UPI — staff verify receipt; the app never assumes success
        if (o.paymentMethod == PaymentMethod.UPI_AT_RESTAURANT && o.paymentStatus == "PENDING" && o.status.isActive && !info?.upiId.isNullOrBlank()) {
            AppCard {
                Text(stringResource(R.string.pay_by_upi_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.pay_by_upi_body, info!!.upiId!!), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                Spacer(Modifier.height(Spacing.m))
                AppButton(stringResource(R.string.open_upi_app, Money.format(o.totalPaise)),
                    { Intents.upiPay(context, info!!.upiId!!, info!!.name, o.totalPaise, o.orderNumber) }, Modifier.fillMaxWidth())
            }
        }

        // Items + bill
        AppCard {
            Text(stringResource(R.string.items), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.s))
            o.items.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    FoodTypeMark(l.foodType, IconSize.xs)
                    Spacer(Modifier.width(Spacing.s))
                    Text("${l.quantity} × ${l.name}" + if (l.options.isNotEmpty()) " (${l.options.joinToString()})" else "", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(l.lineTotalPaise), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionDivider()
            KeyValueRow(stringResource(R.string.subtotal), Money.format(o.subtotalPaise))
            if (o.discountPaise > 0) KeyValueRow(stringResource(R.string.discount_code, o.couponCode ?: ""), "−" + Money.format(o.discountPaise), valueColor = AppTheme.colors.success)
            if (o.taxPaise > 0) KeyValueRow(stringResource(R.string.tax), Money.format(o.taxPaise))
            if (o.packagingFeePaise > 0) KeyValueRow(stringResource(R.string.packaging_fee), Money.format(o.packagingFeePaise))
            if (o.deliveryFeePaise > 0) KeyValueRow(stringResource(R.string.delivery_fee), Money.format(o.deliveryFeePaise))
            SectionDivider()
            KeyValueRow(stringResource(R.string.total), Money.format(o.totalPaise), emphasize = true)
            Text("${stringResource(R.string.payment)}: ${paymentStatusLabel(o.paymentStatus)}", style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }

        // Actions
        if (o.status == OrderStatus.COMPLETED && !feedbackGiven) {
            AppButton(stringResource(R.string.rate_order), { onFeedback(o.accessToken) }, Modifier.fillMaxWidth(), large = true, icon = Icons.Default.Star)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            AppButton(stringResource(R.string.call_restaurant), { Intents.call(context, phone) }, Modifier.weight(1f),
                variant = ButtonVariant.Secondary, icon = Icons.Default.Call)
            info?.let { i ->
                AppButton(stringResource(R.string.directions), { Intents.directions(context, i) }, Modifier.weight(1f),
                    variant = ButtonVariant.Secondary, icon = Icons.Default.Directions)
            }
        }
        if (o.canCancel) {
            AppButton(stringResource(R.string.cancel_order), { confirmCancel = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
        }
        AppButton(stringResource(R.string.back_to_home), onHome, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }

    if (confirmCancel) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.cancel_order_q)) },
            text = {
                AppTextField(reason, { reason = it.take(200) }, stringResource(R.string.reason_optional), singleLine = false)
            },
            confirmButton = {
                TextButton({ vm.cancel(reason.ifBlank { "Cancelled by customer" }); confirmCancel = false }) {
                    Text(stringResource(R.string.cancel_order), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmCancel = false }) { Text(stringResource(R.string.keep_order)) } },
        )
    }
    cancelError?.let { msg ->
        AlertDialog(onDismissRequest = { vm.cancelError.value = null }, text = { Text(msg) },
            confirmButton = { TextButton({ vm.cancelError.value = null }) { Text(stringResource(R.string.ok)) } })
    }
}

@Composable
private fun Timeline(o: Order) {
    val steps = buildList {
        add(OrderStatus.PLACED); add(OrderStatus.ACCEPTED); add(OrderStatus.PREPARING); add(OrderStatus.READY)
        if (o.orderType == OrderType.DELIVERY) add(OrderStatus.OUT_FOR_DELIVERY)
        add(OrderStatus.COMPLETED)
    }
    val reached = o.history.associate { it.status to it.at }
    val failed = o.status in setOf(OrderStatus.REJECTED, OrderStatus.CANCELLED)
    steps.forEach { st ->
        val at = reached[st]
        Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (at != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                tint = if (at != null) AppTheme.colors.success else MaterialTheme.colorScheme.outline, modifier = Modifier.size(Size.stepIcon))
            Spacer(Modifier.width(Spacing.s))
            Text(statusLabel(st), Modifier.weight(1f))
            at?.let { Text(TimeFmt.time(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (failed) {
        Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(Size.stepIcon))
            Spacer(Modifier.width(Spacing.s))
            Text(statusLabel(o.status), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            reached[o.status]?.let { Text(TimeFmt.time(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Colour tone for an order status chip: success when done, error when rejected/cancelled, brand while in progress. */
fun statusTone(s: OrderStatus): Tone = when (s) {
    OrderStatus.COMPLETED -> Tone.Success
    OrderStatus.REJECTED, OrderStatus.CANCELLED -> Tone.Error
    OrderStatus.READY -> Tone.Success
    OrderStatus.UNKNOWN -> Tone.Neutral
    else -> Tone.Brand
}
