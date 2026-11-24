package com.swadsangam.restaurant.ui.more

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.components.ListRow
import com.swadsangam.restaurant.ui.components.BottomActionBar
import com.swadsangam.restaurant.ui.components.ButtonVariant
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swadsangam.restaurant.BuildConfig
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.data.prefs.UserPrefs
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.CartRepository
import com.swadsangam.restaurant.data.repo.MenuRepository
import com.swadsangam.restaurant.data.repo.OrderRepository
import com.swadsangam.restaurant.domain.RestaurantInfo
import com.swadsangam.restaurant.util.Intents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    menu: MenuRepository,
    private val orders: OrderRepository,
    private val cart: CartRepository,
    private val prefs: UserPrefs,
    private val analytics: Analytics,
    private val auth: com.swadsangam.restaurant.data.auth.AuthRepository,
) : ViewModel() {
    val account = auth.account
    val info = menu.restaurant.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun track(e: String) = analytics.track(e)

    /** Removes everything this app stored on the phone (orders list, cart, saved name/phone). */
    fun clearLocalData() = viewModelScope.launch {
        // Also log out: otherwise the saved login would sync the orders straight back
        runCatching { auth.signOut() }
        orders.clearHistory(); cart.clear(); prefs.clearAll()
    }
}

@Composable
fun MoreScreen(onInfo: () -> Unit, onMenu: () -> Unit, onFeedback: () -> Unit, onAccount: () -> Unit,
               vm: MoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var langDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val account by vm.account.collectAsStateWithLifecycle()
    val i = info ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        // Profile header: greeting when signed in, a reason to sign in when not
        AppCard(onClick = onAccount) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Surface(Modifier.size(Size.avatar), shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        val name = account?.profile?.fullName?.takeIf { it.isNotBlank() }
                        if (name != null) Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                        else Icon(Icons.Default.AccountCircle, null)
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    val name = account?.profile?.fullName?.takeIf { it.isNotBlank() }
                    Text(if (name != null) stringResource(R.string.hello_name, name) else stringResource(R.string.hello_guest), style = MaterialTheme.typography.titleMedium)
                    Text(account?.email ?: stringResource(R.string.sign_in_benefit), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondaryText)
            }
        }
        Group {
            ListRow(stringResource(R.string.acc_account), Icons.Default.AccountCircle, onAccount)
            ListRow(stringResource(R.string.restaurant_info), Icons.Default.Info, onInfo)
            ListRow(stringResource(R.string.view_menu), Icons.Default.RestaurantMenu, onMenu)
        }
        Group {
            ListRow(stringResource(R.string.call_restaurant), Icons.Default.Call, { vm.track(Analytics.CALL); Intents.call(context, i.phone) })
            ListRow(stringResource(R.string.get_directions), Icons.Default.Directions, { vm.track(Analytics.DIRECTIONS); Intents.directions(context, i) })
            i.whatsappPhone?.let { wa ->
                ListRow(stringResource(R.string.whatsapp), Icons.AutoMirrored.Filled.Chat, { Intents.openUrl(context, "https://wa.me/" + wa.filter { it.isDigit() }) })
            }
            i.supportEmail?.let { email -> ListRow(stringResource(R.string.support_email, email), Icons.Default.Email, { Intents.email(context, email) }) }
            ListRow(stringResource(R.string.share_restaurant), Icons.Default.Share, { vm.track(Analytics.SHARE); Intents.share(context, i) })
            ListRow(stringResource(R.string.give_feedback), Icons.Default.RateReview, onFeedback)
        }
        Group {
            ListRow(stringResource(R.string.language), Icons.Default.Language, { langDialog = true })
            i.privacyPolicyUrl?.let { url -> ListRow(stringResource(R.string.privacy_policy), Icons.Default.Policy, { Intents.openUrl(context, url) }) }
            i.termsUrl?.let { url -> ListRow(stringResource(R.string.order_terms), Icons.Default.Policy, { Intents.openUrl(context, url) }) }
            ListRow(stringResource(R.string.clear_my_data), Icons.Default.DeleteOutline, { clearDialog = true }, showChevron = false)
        }
        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME) + (i.fssaiNumber?.let { "\n" + stringResource(R.string.fssai_no, it) } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = secondaryText, modifier = Modifier.padding(horizontal = Spacing.s),
        )
    }

    if (langDialog) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        AlertDialog(
            onDismissRequest = { langDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    listOf("" to stringResource(R.string.lang_system), "en" to "English", "hi" to "हिन्दी").forEach { (tag, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)); langDialog = false
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == tag, null)
                            Spacer(Modifier.width(Spacing.s))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ langDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.clear_my_data)) },
            text = { Text(stringResource(R.string.clear_my_data_body)) },
            confirmButton = { TextButton({ vm.clearLocalData(); clearDialog = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton({ clearDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A rounded card holding a group of [ListRow]s (settings-style list). */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    AppCard(padding = PaddingValues(vertical = Spacing.xs), content = content)
}

/** FR-11 restaurant information. */
@Composable
fun RestaurantInfoScreen(onMenu: () -> Unit, vm: MoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val i = info ?: return
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Column {
                Text(i.name, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.cuisine_line), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                i.rating?.let {
                    Text(stringResource(R.string.rating_value, it.toString()) + (i.reviewCount?.let { n -> " · " + stringResource(R.string.public_reviews, n) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            AppCard {
                Block(stringResource(R.string.address)) { Text(i.address, style = MaterialTheme.typography.bodyMedium) }
                Block(stringResource(R.string.phone)) { Text(i.phone, style = MaterialTheme.typography.bodyMedium) }
                Block(stringResource(R.string.opening_hours)) { HoursText(i) }
            }
            AppCard {
                Block(stringResource(R.string.food_categories)) { Text(stringResource(R.string.food_categories_value), style = MaterialTheme.typography.bodyMedium) }
                Block(stringResource(R.string.dine_in)) { Text(i.dineInInfo ?: stringResource(R.string.dine_in_default), style = MaterialTheme.typography.bodyMedium) }
                i.parkingInfo?.let { Block(stringResource(R.string.parking)) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                if (i.deliveryEnabled) Block(stringResource(R.string.delivery)) { Text(i.deliveryAreaNote ?: stringResource(R.string.delivery_available), style = MaterialTheme.typography.bodyMedium) }
                i.supportEmail?.let { Block(stringResource(R.string.customer_support)) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                i.fssaiNumber?.let { Block(stringResource(R.string.fssai)) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                i.gstin?.let { Block("GSTIN") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
            AppButton(stringResource(R.string.view_menu), onMenu, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, icon = Icons.Default.RestaurantMenu)
        }
        BottomActionBar {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppButton(stringResource(R.string.call), { vm.track(Analytics.CALL); Intents.call(context, i.phone) }, Modifier.weight(1f), icon = Icons.Default.Call)
                AppButton(stringResource(R.string.directions), { vm.track(Analytics.DIRECTIONS); Intents.directions(context, i) }, Modifier.weight(1f),
                    variant = ButtonVariant.Secondary, icon = Icons.Default.Directions)
                IconButton({ vm.track(Analytics.SHARE); Intents.share(context, i) }, Modifier.size(Size.touch)) {
                    Icon(Icons.Default.Share, stringResource(R.string.share_restaurant), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun HoursText(i: RestaurantInfo) {
    val days = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun)
    val all = (1..7).map { i.openingHours[it].orEmpty() }
    if (all.distinct().size == 1) {
        val slots = all.first()
        Text(stringResource(R.string.daily) + ": " + if (slots.isEmpty()) stringResource(R.string.closed) else slots.joinToString { "${fmt(it.open)}–${fmt(it.close)}" })
    } else {
        days.forEachIndexed { idx, d ->
            val slots = all[idx]
            Text(stringResource(d) + ": " + if (slots.isEmpty()) stringResource(R.string.closed) else slots.joinToString { "${fmt(it.open)}–${fmt(it.close)}" })
        }
    }
    if (!i.hoursVerified) {
        Text(stringResource(R.string.hours_unverified), style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

private fun fmt(hhmm: String): String {
    val p = hhmm.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return hhmm
    val m = p.getOrNull(1) ?: "00"
    val suffix = if (h < 12 || h == 24) "AM" else "PM"
    val h12 = when { h == 0 || h == 24 -> 12; h > 12 -> h - 12; else -> h }
    return "$h12:$m $suffix"
}
