package com.swadsangam.restaurant.ui.checkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.OrderType
import com.swadsangam.restaurant.domain.PaymentMethod
import com.swadsangam.restaurant.ui.cart.PriceSummary
import com.swadsangam.restaurant.ui.cart.orderTypeLabel
import com.swadsangam.restaurant.ui.components.InfoBanner
import com.swadsangam.restaurant.util.Intents

@Composable
fun CheckoutScreen(
    hindi: Boolean,
    tableNumber: String?,
    onPlaced: (token: String) -> Unit,
    onBackToCart: () -> Unit,
    vm: CheckoutViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val u by vm.ui.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val quote by vm.quote.collectAsStateWithLifecycle()
    val placed by vm.placed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.start(tableNumber) }
    LaunchedEffect(placed) { placed?.let { onPlaced(it.accessToken) } }

    val i = info ?: return
    val err = u.showErrors

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.your_details), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.no_account_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(u.name, { v -> vm.edit { it.copy(name = v.take(60)) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name)) }, singleLine = true,
                isError = err && vm.nameError(u), supportingText = if (err && vm.nameError(u)) { { Text(stringResource(R.string.name_error)) } } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(u.phone, { v -> vm.edit { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16)) } },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.mobile_number)) }, singleLine = true,
                prefix = { Text("+91 ") },
                isError = err && vm.phoneError(u), supportingText = if (err && vm.phoneError(u)) { { Text(stringResource(R.string.phone_error)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.order_type), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                i.enabledOrderTypes.forEach { t ->
                    FilterChip(u.orderType == t, { vm.edit { it.copy(orderType = t) } }, label = { Text(orderTypeLabel(t)) })
                }
            }
            when (u.orderType) {
                OrderType.PICKUP -> {
                    Text(stringResource(R.string.pickup_from, i.address), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(u.instructions, { v -> vm.edit { it.copy(instructions = v.take(300)) } }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pickup_instructions)) })
                }
                OrderType.DINE_IN -> {
                    i.dineInInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(u.tableNumber, { v -> vm.edit { it.copy(tableNumber = v.take(10)) } }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.table_number)) }, singleLine = true, isError = err && vm.tableError(u))
                }
                OrderType.DELIVERY -> {
                    (i.deliveryAreaNote ?: i.deliveryRadiusKm?.let { stringResource(R.string.delivery_radius, it.toString()) })?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(u.address, { v -> vm.edit { it.copy(address = v.take(300)) } }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.delivery_address)) }, minLines = 2,
                        isError = err && vm.addressError(u),
                        supportingText = if (err && vm.addressError(u)) { { Text(stringResource(R.string.address_error)) } } else null)
                    OutlinedTextField(u.landmark, { v -> vm.edit { it.copy(landmark = v.take(120)) } }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.landmark)) }, singleLine = true)
                }
            }
            OutlinedTextField(u.notes, { v -> vm.edit { it.copy(notes = v.take(300)) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.order_notes)) })

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleMedium)
            i.enabledPaymentMethods.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.edit { it.copy(payment = m) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(u.payment == m, { vm.edit { it.copy(payment = m) } })
                    Text(paymentLabel(m, u.orderType))
                }
            }
            Text(stringResource(R.string.online_payment_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.order_summary), style = MaterialTheme.typography.titleMedium)
            lines.filter { it.isOrderable }.forEach { l ->
                Text("${l.quantity} × ${l.item?.displayName(hindi)}" +
                    (if (l.selectedOptions.isNotEmpty()) " (${l.selectedOptions.joinToString { it.name }})" else "") +
                    " — ${Money.format(l.lineTotalPaise)}", style = MaterialTheme.typography.bodyMedium)
            }
            quote?.let { PriceSummary(it, null, verified = true) }

            Row(Modifier.fillMaxWidth().clickable { vm.edit { it.copy(termsAccepted = !it.termsAccepted) } }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(u.termsAccepted, { c -> vm.edit { it.copy(termsAccepted = c) } })
                Text(stringResource(R.string.terms_checkbox), style = MaterialTheme.typography.bodySmall,
                    color = if (err && !u.termsAccepted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            i.termsUrl?.let { url -> TextButton({ Intents.openUrl(context, url) }) { Text(stringResource(R.string.read_terms)) } }
            if (u.error is CheckoutError.Network) {
                InfoBanner(stringResource(R.string.network_error_retry), isError = true)
            }
        }
        HorizontalDivider()
        Button(
            onClick = { vm.submit() }, enabled = !u.submitting && lines.any { it.isOrderable },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) {
            if (u.submitting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.place_order, Money.format(quote?.totalPaise ?: 0)))
        }
    }

    when (val e = u.error) {
        is CheckoutError.PriceChanged -> AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(stringResource(R.string.price_changed_title)) },
            text = { Text(stringResource(R.string.price_changed_body, Money.format(e.newTotalPaise))) },
            confirmButton = { TextButton(vm::dismissError) { Text(stringResource(R.string.review_order)) } },
        )
        is CheckoutError.Server -> AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(stringResource(R.string.order_not_placed)) },
            text = { Text(e.message) },
            confirmButton = {
                if (e.code in setOf("ITEM_UNAVAILABLE", "BELOW_MINIMUM_ORDER", "INVALID_COUPON", "COUPON_MIN_ORDER")) {
                    TextButton({ vm.dismissError(); onBackToCart() }) { Text(stringResource(R.string.back_to_cart)) }
                } else TextButton(vm::dismissError) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton({ Intents.call(context, i.phone) }) { Text(stringResource(R.string.call_restaurant)) }
            },
        )
        else -> Unit
    }
}

@Composable
private fun paymentLabel(m: PaymentMethod, type: OrderType): String = when (m) {
    PaymentMethod.CASH -> stringResource(
        when (type) {
            OrderType.PICKUP -> R.string.cash_on_pickup
            OrderType.DINE_IN -> R.string.cash_at_restaurant
            OrderType.DELIVERY -> R.string.cash_on_delivery
        },
    )
    PaymentMethod.UPI_AT_RESTAURANT -> stringResource(R.string.upi_at_restaurant)
    PaymentMethod.ONLINE -> stringResource(R.string.online_payment)
}
