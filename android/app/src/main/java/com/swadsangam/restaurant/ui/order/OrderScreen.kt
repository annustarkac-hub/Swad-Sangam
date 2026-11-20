package com.swadsangam.restaurant.ui.order

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
import com.swadsangam.restaurant.ui.theme.SuccessGreen
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

    val o = order ?: run { EmptyState(stringResource(R.string.loading_order)); return }
    val phone = info?.phone ?: o.restaurantPhone

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.order_number, o.orderNumber), style = MaterialTheme.typography.headlineSmall)
        Text("${orderTypeLabel(o.orderType)}${o.tableNumber?.let { " · " + stringResource(R.string.table_n, it) } ?: ""} · ${TimeFmt.dateTime(o.createdAt)}",
            style = MaterialTheme.typography.bodySmall)
        if (offline) InfoBanner(stringResource(R.string.status_offline))

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(statusLabel(o.status), style = MaterialTheme.typography.titleLarge,
                    color = if (o.status in setOf(OrderStatus.REJECTED, OrderStatus.CANCELLED)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                // Never show "accepted" until the restaurant actually accepts (FR-09)
                if (o.status == OrderStatus.PLACED) {
                    Text(stringResource(R.string.awaiting_confirmation), style = MaterialTheme.typography.bodyMedium)
                    val waitingMin = TimeFmt.minutesSince(o.createdAt, now)
                    if (waitingMin >= 2) {
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(stringResource(R.string.not_confirmed_call), action = stringResource(R.string.call),
                            onAction = { Intents.call(context, phone) })
                    }
                }
                o.rejectionReason?.let { Text(stringResource(R.string.reason_prefix, it)) }
                o.cancelReason?.let { Text(stringResource(R.string.reason_prefix, it)) }
                if (o.status.isActive && o.status != OrderStatus.PLACED) {
                    o.estimatedReadyAt?.let { Text(stringResource(R.string.estimated_ready, TimeFmt.time(it))) }
                }
                Spacer(Modifier.height(12.dp))
                Timeline(o)
            }
        }

        if (o.status == OrderStatus.READY && o.orderType == OrderType.PICKUP) {
            InfoBanner(stringResource(R.string.ready_collect, info?.address ?: ""))
        }

        // Pay at restaurant via UPI — staff verify receipt; the app never assumes success
        if (o.paymentMethod == PaymentMethod.UPI_AT_RESTAURANT && o.paymentStatus == "PENDING" && o.status.isActive && !info?.upiId.isNullOrBlank()) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.pay_by_upi_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pay_by_upi_body, info!!.upiId!!), style = MaterialTheme.typography.bodySmall)
                    Button({ Intents.upiPay(context, info!!.upiId!!, info!!.name, o.totalPaise, o.orderNumber) }, Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.open_upi_app, Money.format(o.totalPaise)))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.items), style = MaterialTheme.typography.titleMedium)
        o.items.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodTypeMark(l.foodType, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text("${l.quantity} × ${l.name}" + if (l.options.isNotEmpty()) " (${l.options.joinToString()})" else "", Modifier.weight(1f))
                Text(Money.format(l.lineTotalPaise))
            }
        }
        Spacer(Modifier.height(8.dp))
        Amount(stringResource(R.string.subtotal), o.subtotalPaise)
        if (o.discountPaise > 0) Amount(stringResource(R.string.discount_code, o.couponCode ?: ""), -o.discountPaise)
        if (o.taxPaise > 0) Amount(stringResource(R.string.tax), o.taxPaise)
        if (o.packagingFeePaise > 0) Amount(stringResource(R.string.packaging_fee), o.packagingFeePaise)
        if (o.deliveryFeePaise > 0) Amount(stringResource(R.string.delivery_fee), o.deliveryFeePaise)
        Amount(stringResource(R.string.total), o.totalPaise, bold = true)
        Text("${stringResource(R.string.payment)}: ${paymentStatusLabel(o.paymentStatus)}", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ Intents.call(context, phone) }, Modifier.weight(1f)) { Text(stringResource(R.string.call_restaurant)) }
            info?.let { i -> OutlinedButton({ Intents.directions(context, i) }, Modifier.weight(1f)) { Text(stringResource(R.string.directions)) } }
        }
        if (o.canCancel) {
            TextButton({ confirmCancel = true }) { Text(stringResource(R.string.cancel_order), color = MaterialTheme.colorScheme.error) }
        }
        if (o.status == OrderStatus.COMPLETED && !feedbackGiven) {
            Button({ onFeedback(o.accessToken) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.rate_order)) }
        }
        TextButton(onHome) { Text(stringResource(R.string.back_to_home)) }
    }

    if (confirmCancel) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.cancel_order_q)) },
            text = {
                OutlinedTextField(reason, { reason = it.take(200) }, label = { Text(stringResource(R.string.reason_optional)) })
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
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (at != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                tint = if (at != null) SuccessGreen else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(statusLabel(st), Modifier.weight(1f))
            at?.let { Text(TimeFmt.time(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (failed) {
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(statusLabel(o.status), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            reached[o.status]?.let { Text(TimeFmt.time(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Amount(label: String, paise: Int, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text((if (paise < 0) "−" else "") + Money.format(kotlin.math.abs(paise)),
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}
