package com.swadsangam.restaurant.ui.checkout

import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.PaddingValues
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusBanner
import com.swadsangam.restaurant.ui.components.KeyValueRow
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.BottomActionBar
import com.swadsangam.restaurant.ui.components.AppTextField
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
    var editDetails by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    val u by vm.ui.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val quote by vm.quote.collectAsStateWithLifecycle()
    val quoteVerified by vm.quoteVerified.collectAsStateWithLifecycle()
    val couponCode by vm.couponCode.collectAsStateWithLifecycle()
    val placed by vm.placed.collectAsStateWithLifecycle()
    val account by vm.addresses.collectAsStateWithLifecycle()
    val deliveryNotice by vm.deliveryNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.start(tableNumber) }
    LaunchedEffect(placed) { placed?.let { onPlaced(it.accessToken) } }

    val i = info ?: return
    val err = u.showErrors

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            // 1. Your details
            AppCard {
                Text(stringResource(R.string.your_details), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_account_needed), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                // Returning customer with saved, valid details: one summary line + Edit (repeat checkout in two taps)
                val detailsValid = !vm.nameError(u) && !vm.phoneError(u)
                LaunchedEffect(u.name.isNotBlank()) { if (editDetails == null && u.name.isNotBlank()) editDetails = !detailsValid }
                if (detailsValid && editDetails == false) {
                    Spacer(Modifier.height(Spacing.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleSmall)
                            Text("+91 " + u.phone.filter(Char::isDigit).takeLast(10), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                        }
                        AppButton(stringResource(R.string.acc_edit), { editDetails = true }, variant = ButtonVariant.Text)
                    }
                } else {
                Spacer(Modifier.height(Spacing.m))
                    AppTextField(u.name, { v -> vm.edit { it.copy(name = v.take(60)) } }, stringResource(R.string.name),
                        error = if (err && vm.nameError(u)) stringResource(R.string.name_error) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Spacer(Modifier.height(Spacing.s))
                    AppTextField(u.phone, { v -> vm.edit { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16)) } },
                        stringResource(R.string.mobile_number), prefix = "+91 ",
                        error = if (err && vm.phoneError(u)) stringResource(R.string.phone_error) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
            }

            // 2. Pickup, table or delivery
            AppCard {
                Text(stringResource(R.string.order_type), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    i.enabledOrderTypes.forEach { t ->
                        FilterChip(u.orderType == t, { vm.edit { it.copy(orderType = t) } }, label = { Text(orderTypeLabel(t)) },
                            modifier = Modifier.heightIn(min = Size.touch))
                    }
                }
                Spacer(Modifier.height(Spacing.s))
                when (u.orderType) {
                    OrderType.PICKUP -> {
                        Text(stringResource(R.string.pickup_from, i.address), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                        Spacer(Modifier.height(Spacing.s))
                        AppTextField(u.instructions, { v -> vm.edit { it.copy(instructions = v.take(300)) } }, stringResource(R.string.pickup_instructions), singleLine = false)
                    }
                    OrderType.DINE_IN -> {
                        i.dineInInfo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondaryText); Spacer(Modifier.height(Spacing.s)) }
                        AppTextField(u.tableNumber, { v -> vm.edit { it.copy(tableNumber = v.take(10)) } }, stringResource(R.string.table_number),
                            error = if (err && vm.tableError(u)) stringResource(R.string.table_number) else null)
                        if (i.requireTableCode) {
                            Spacer(Modifier.height(Spacing.s))
                            AppTextField(u.tableCode, { v -> vm.edit { it.copy(tableCode = v.filter(Char::isDigit).take(4)) } }, stringResource(R.string.table_code),
                                error = if (err && vm.tableCodeError(u, i.requireTableCode)) stringResource(R.string.table_code_error) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        }
                    }
                    OrderType.DELIVERY -> {
                        (i.deliveryAreaNote ?: i.deliveryRadiusKm?.let { stringResource(R.string.delivery_radius, it.toString()) })?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = secondaryText); Spacer(Modifier.height(Spacing.s))
                        }
                        account?.addresses?.takeIf { it.isNotEmpty() }?.let { saved ->
                            Text(stringResource(R.string.saved_address), style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                saved.forEach { a ->
                                    FilterChip(u.address == a.address, { vm.chooseAddress(a) },
                                        label = { Text(a.label) }, modifier = Modifier.heightIn(min = Size.touch))
                                }
                                FilterChip(saved.none { it.address == u.address }, { vm.chooseAddress(null) },
                                    label = { Text(stringResource(R.string.new_address)) }, modifier = Modifier.heightIn(min = Size.touch))
                            }
                            Spacer(Modifier.height(Spacing.s))
                        }
                        AppTextField(u.address, { v -> vm.edit { it.copy(address = v.take(300)) } }, stringResource(R.string.delivery_address),
                            singleLine = false, minLines = 2, error = if (err && vm.addressError(u)) stringResource(R.string.address_error) else null)
                        Spacer(Modifier.height(Spacing.s))
                        AppTextField(u.landmark, { v -> vm.edit { it.copy(landmark = v.take(120)) } }, stringResource(R.string.landmark))
                        Spacer(Modifier.height(Spacing.s))
                        val ctx = LocalContext.current
                        val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                            if (r.values.any { it }) vm.locate(ctx) else vm.locationDenied()
                        }
                        AppButton(
                            when {
                                u.locating -> stringResource(R.string.locating)
                                u.pinLat != null -> stringResource(R.string.location_added)
                                else -> stringResource(R.string.use_my_location)
                            },
                            {
                                val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
                                if (granted) vm.locate(ctx)
                                else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            variant = ButtonVariant.Secondary, enabled = !u.locating, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(if (u.locationFailed) R.string.location_failed else R.string.location_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (u.locationFailed) MaterialTheme.colorScheme.error else secondaryText,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        deliveryNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.xs)) }
                    }
                }
                Spacer(Modifier.height(Spacing.s))
                AppTextField(u.notes, { v -> vm.edit { it.copy(notes = v.take(300)) } }, stringResource(R.string.order_notes), singleLine = false)
            }

            // 3. Payment
            AppCard(padding = PaddingValues(top = Spacing.l, bottom = Spacing.s)) {
                Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.l))
                i.enabledPaymentMethods.forEach { m ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Size.touch)
                        .selectable(selected = u.payment == m, role = Role.RadioButton) { vm.edit { it.copy(payment = m) } }
                        .padding(horizontal = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(u.payment == m, null, Modifier.padding(Spacing.s))
                        Text(paymentLabel(m, u.orderType), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(stringResource(R.string.online_payment_later), style = MaterialTheme.typography.bodySmall, color = secondaryText,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs))
            }

            // 4. Summary + bill
            AppCard {
                Text(stringResource(R.string.order_summary), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.s))
                lines.filter { it.isOrderable }.forEach { l ->
                    KeyValueRow("${l.quantity} × ${l.item?.displayName(hindi)}" +
                        (if (l.selectedOptions.isNotEmpty()) " (${l.selectedOptions.joinToString { it.name }})" else ""), Money.format(l.lineTotalPaise))
                }
            }
            quote?.let { PriceSummary(it, couponCode.ifBlank { null }, verified = quoteVerified) }
        }
        BottomActionBar {
            Row(Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable { vm.edit { it.copy(termsAccepted = !it.termsAccepted) } },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(u.termsAccepted, { c -> vm.edit { it.copy(termsAccepted = c) } })
                Text(stringResource(R.string.terms_checkbox), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (err && !u.termsAccepted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                i.termsUrl?.let { url -> AppButton(stringResource(R.string.read_terms), { Intents.openUrl(context, url) }, variant = ButtonVariant.Text) }
            }
            if (u.error is CheckoutError.Network) StatusBanner(stringResource(R.string.network_error_retry), tone = Tone.Error)
            AppButton(stringResource(R.string.place_order, Money.format(quote?.totalPaise ?: 0)), { vm.submit() }, Modifier.fillMaxWidth(),
                large = true, loading = u.submitting, enabled = lines.any { it.isOrderable })
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
                    AppButton(stringResource(R.string.back_to_cart), { vm.dismissError(); onBackToCart() }, variant = ButtonVariant.Text)
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
