package com.swadsangam.restaurant.ui.account

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusChip
import com.swadsangam.restaurant.ui.components.StatusBanner
import com.swadsangam.restaurant.ui.components.LoadingState
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.AppTextField
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.PhoneValidator
import com.swadsangam.restaurant.data.auth.AuthRepository
import com.swadsangam.restaurant.data.auth.AuthResult
import com.swadsangam.restaurant.data.remote.AddressDto
import com.swadsangam.restaurant.data.remote.AddressWrite
import com.swadsangam.restaurant.ui.components.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    val account = auth.account
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<Pair<Boolean, String>?>(null)

    init { viewModelScope.launch { auth.reload(); loaded = true } }

    fun run(block: suspend () -> Unit) = viewModelScope.launch { busy = true; message = null; try { block() } finally { busy = false } }
    fun signIn(e: String, p: String, msg: (AuthResult) -> Pair<Boolean, String>?) = run { message = msg(auth.signIn(e, p)) }
    fun signUp(e: String, p: String, n: String, ph: String, msg: (AuthResult) -> Pair<Boolean, String>?) = run { message = msg(auth.signUp(e, p, n, ph)) }
    fun reset(e: String, msg: (AuthResult) -> Pair<Boolean, String>?) = run { message = msg(auth.sendPasswordReset(e)) }
    fun signOut() = run { auth.signOut() }
    fun saveProfile(n: String, ph: String, ok: String, err: String) = run { message = if (auth.saveProfile(n, ph).isSuccess) true to ok else false to err }
    fun saveAddress(id: Long?, a: AddressWrite, onDone: () -> Unit, err: (String) -> String) = run {
        auth.saveAddress(id, a).onSuccess { onDone() }.onFailure { message = false to err(it.message.orEmpty()) }
    }
    fun deleteAddress(id: Long) = run { auth.deleteAddress(id) }
}

/** Optional customer account: free email + password login (no OTP). Guests can still order. */
@Composable
fun AccountScreen(onMyOrders: () -> Unit, vm: AccountViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val account by vm.account.collectAsStateWithLifecycle()
    if (!vm.loaded) { LoadingState(stringResource(R.string.loading)); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        when {
            account == null -> AuthForm(vm)
            else -> ProfileSection(vm, account!!.email, account!!.profile?.fullName.orEmpty(), account!!.profile?.phone.orEmpty(), account!!.addresses, onMyOrders)
        }
        vm.message?.let { (ok, text) ->
            StatusBanner(text, tone = if (ok) Tone.Success else Tone.Error)
        }
    }
}

@Composable
private fun AuthForm(vm: AccountViewModel) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val msgs = AuthMessages.current()
    Text(stringResource(if (mode == "signup") R.string.acc_create else if (mode == "forgot") R.string.acc_reset else R.string.acc_login),
        style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.acc_why), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
    if (mode == "signup") {
        AppTextField(name, { name = it.take(60) }, stringResource(R.string.name), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        AppTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, stringResource(R.string.mobile_number),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
    }
    AppTextField(email, { email = it.trim() }, stringResource(R.string.acc_email),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
    if (mode != "forgot") {
        AppTextField(password, { password = it }, stringResource(R.string.acc_password), supporting = if (mode == "signup") stringResource(R.string.acc_short_password) else null,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
    }
    val invalidEmail = stringResource(R.string.acc_bad_email)
    val badDetails = stringResource(R.string.acc_bad_details)
    val shortPass = stringResource(R.string.acc_short_password)
    AppButton(
        text = stringResource(if (mode == "signup") R.string.acc_create else if (mode == "forgot") R.string.acc_send_reset else R.string.acc_login),
        onClick = {
            when {
                !Regex("^\\S+@\\S+\\.\\S+$").matches(email) -> vm.message = false to invalidEmail
                mode == "forgot" -> vm.reset(email) { r -> if (r is AuthResult.Error) false to msgs.error(r.message) else true to msgs.resetSent }
                password.length < 6 -> vm.message = false to shortPass
                mode == "login" -> vm.signIn(email, password) { r -> (r as? AuthResult.Error)?.let { false to msgs.error(it.message) } }
                else -> {
                    val ph = PhoneValidator.normalize(phone)
                    if (name.trim().length < 2 || ph == null) vm.message = false to badDetails
                    else vm.signUp(email, password, name, ph) { r ->
                        when (r) {
                            is AuthResult.Error -> false to msgs.error(r.message)
                            AuthResult.ConfirmEmail -> true to msgs.confirmEmail
                            AuthResult.SignedIn -> null
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(), large = true, enabled = !vm.busy, loading = vm.busy,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (mode == "login") AppButton(stringResource(R.string.acc_new_here), { mode = "signup"; vm.message = null }, variant = ButtonVariant.Text)
        else AppButton(stringResource(R.string.acc_have_account), { mode = "login"; vm.message = null }, variant = ButtonVariant.Text)
        if (mode == "login") AppButton(stringResource(R.string.acc_forgot), { mode = "forgot"; vm.message = null }, variant = ButtonVariant.Text)
    }
    StatusBanner(stringResource(R.string.acc_guest_ok), tone = Tone.Neutral, icon = Icons.Default.Info)
}

@Composable
private fun ProfileSection(vm: AccountViewModel, email: String, savedName: String, savedPhone: String, addresses: List<AddressDto>, onMyOrders: () -> Unit) {
    var name by remember(savedName) { mutableStateOf(savedName) }
    var phone by remember(savedPhone) { mutableStateOf(savedPhone) }
    var editing by remember { mutableStateOf<AddressDto?>(null) }
    var adding by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.acc_saved)
    val failed = stringResource(R.string.acc_save_failed)
    val badDetails = stringResource(R.string.acc_bad_details)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(Size.avatar), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text((savedName.ifBlank { email }).take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.acc_profile), style = MaterialTheme.typography.headlineSmall)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    AppCard {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
    AppTextField(name, { name = it.take(60) }, stringResource(R.string.name), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
    AppTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, stringResource(R.string.mobile_number),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    AppButton(stringResource(R.string.acc_save_profile), {
        val ph = PhoneValidator.normalize(phone)
        if (name.trim().length < 2 || ph == null) vm.message = false to badDetails else vm.saveProfile(name, ph, saved, failed)
    }, Modifier.fillMaxWidth(), enabled = !vm.busy, loading = vm.busy)
      }
    }

    Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.acc_addresses), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!adding && editing == null && addresses.size < 10) AppButton(stringResource(R.string.add), { adding = true }, variant = ButtonVariant.Text, icon = Icons.Default.Add)
    }
    if (addresses.isEmpty() && !adding) Text(stringResource(R.string.acc_no_address), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
    addresses.forEach { a ->
        if (editing?.id == a.id) AddressForm(vm, a, addresses.isEmpty()) { editing = null }
        else AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.s))
                Text(a.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (a.isDefault) StatusChip(stringResource(R.string.acc_default), tone = Tone.Success)
            }
            Text(a.address + (a.landmark?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = secondaryText,
                modifier = Modifier.padding(top = Spacing.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!a.isDefault) AppButton(stringResource(R.string.acc_make_default), { vm.saveAddress(a.id, AddressWrite(a.label, a.address, a.landmark, true), {}) { it } }, variant = ButtonVariant.Text)
                AppButton(stringResource(R.string.acc_edit), { editing = a }, variant = ButtonVariant.Text)
                AppButton(stringResource(R.string.acc_delete), { vm.deleteAddress(a.id) }, variant = ButtonVariant.Danger)
            }
        }
    }
    if (adding) AddressForm(vm, null, addresses.isEmpty()) { adding = false }

    Row(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton(stringResource(R.string.my_orders), onMyOrders, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.AutoMirrored.Filled.ReceiptLong)
        AppButton(stringResource(R.string.acc_logout), { vm.signOut() }, Modifier.weight(1f), variant = ButtonVariant.Text, icon = Icons.AutoMirrored.Filled.Logout)
    }
}

@Composable
private fun AddressForm(vm: AccountViewModel, a: AddressDto?, first: Boolean, onDone: () -> Unit) {
    var label by remember { mutableStateOf(a?.label ?: "Home") }
    var address by remember { mutableStateOf(a?.address.orEmpty()) }
    var landmark by remember { mutableStateOf(a?.landmark.orEmpty()) }
    var isDefault by remember { mutableStateOf(a?.isDefault ?: first) }
    val full = stringResource(R.string.address_error)
    val tooMany = stringResource(R.string.acc_too_many)
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            AppTextField(label, { label = it.take(30) }, stringResource(R.string.acc_label))
            AppTextField(address, { address = it.take(300) }, stringResource(R.string.delivery_address), singleLine = false, minLines = 2)
            AppTextField(landmark, { landmark = it.take(120) }, stringResource(R.string.landmark))
            Row(Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable { isDefault = !isDefault }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(isDefault, { isDefault = it }); Text(stringResource(R.string.acc_make_default), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppButton(stringResource(R.string.acc_save_address), {
                    if (address.trim().length < 10) vm.message = false to full
                    else vm.saveAddress(a?.id, AddressWrite(label.trim().ifBlank { "Home" }, address.trim(), landmark.trim().ifBlank { null }, isDefault), onDone) {
                        if (it.contains("TOO_MANY")) tooMany else it
                    }
                }, Modifier.weight(1f), enabled = !vm.busy, loading = vm.busy)
                AppButton(stringResource(R.string.cancel), onDone, variant = ButtonVariant.Text)
            }
        }
    }
}

/** Friendly texts for auth results. */
private data class AuthMessages(val resetSent: String, val confirmEmail: String, val wrong: String, val exists: String,
                                val network: String, val tooMany: String, val generic: String) {
    fun error(code: String) = when {
        code == "NETWORK" -> network
        code.contains("invalid_credentials", true) || code.contains("Invalid login", true) -> wrong
        code.contains("already", true) || code.contains("exists", true) -> exists
        code.contains("rate", true) || code.contains("too many", true) -> tooMany
        code.contains("email_not_confirmed", true) || code.contains("not confirmed", true) -> confirmEmail
        else -> "$generic ($code)"
    }
    companion object {
        @Composable fun current() = AuthMessages(
            stringResource(R.string.acc_reset_sent), stringResource(R.string.acc_confirm_email), stringResource(R.string.acc_wrong),
            stringResource(R.string.acc_exists), stringResource(R.string.no_internet), stringResource(R.string.acc_too_many_tries),
            stringResource(R.string.acc_generic_error),
        )
    }
}
