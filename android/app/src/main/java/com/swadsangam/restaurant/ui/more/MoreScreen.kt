package com.swadsangam.restaurant.ui.more

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) : ViewModel() {
    val info = menu.restaurant.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun track(e: String) = analytics.track(e)

    /** Removes everything this app stored on the phone (orders list, cart, saved name/phone). */
    fun clearLocalData() = viewModelScope.launch {
        orders.clearHistory(); cart.clear(); prefs.clearAll()
    }
}

@Composable
fun MoreScreen(onInfo: () -> Unit, onMenu: () -> Unit, onFeedback: () -> Unit,
               vm: MoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var langDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val i = info ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Entry(Icons.Default.Info, stringResource(R.string.restaurant_info), onInfo)
        Entry(Icons.Default.RestaurantMenu, stringResource(R.string.view_menu), onMenu)
        Entry(Icons.Default.Call, stringResource(R.string.call_restaurant)) { vm.track(Analytics.CALL); Intents.call(context, i.phone) }
        Entry(Icons.Default.Directions, stringResource(R.string.get_directions)) { vm.track(Analytics.DIRECTIONS); Intents.directions(context, i) }
        Entry(Icons.Default.Share, stringResource(R.string.share_restaurant)) { vm.track(Analytics.SHARE); Intents.share(context, i) }
        Entry(Icons.Default.RateReview, stringResource(R.string.give_feedback), onFeedback)
        i.supportEmail?.let { email -> Entry(Icons.Default.Email, stringResource(R.string.support_email, email)) { Intents.email(context, email) } }
        i.whatsappPhone?.let { wa ->
            Entry(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.whatsapp)) {
                Intents.openUrl(context, "https://wa.me/" + wa.filter { it.isDigit() })
            }
        }
        HorizontalDivider()
        Entry(Icons.Default.Language, stringResource(R.string.language)) { langDialog = true }
        i.privacyPolicyUrl?.let { url -> Entry(Icons.Default.Policy, stringResource(R.string.privacy_policy)) { Intents.openUrl(context, url) } }
        i.termsUrl?.let { url -> Entry(Icons.Default.Policy, stringResource(R.string.order_terms)) { Intents.openUrl(context, url) } }
        Entry(Icons.Default.DeleteOutline, stringResource(R.string.clear_my_data)) { clearDialog = true }
        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME) + (i.fssaiNumber?.let { "\n" + stringResource(R.string.fssai_no, it) } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
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
                        Row(Modifier.fillMaxWidth().clickable {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)); langDialog = false
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == tag, null)
                            Text(label)
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

@Composable
private fun Entry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** FR-11 restaurant information. */
@Composable
fun RestaurantInfoScreen(onMenu: () -> Unit, vm: MoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val i = info ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(i.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.cuisine_line), style = MaterialTheme.typography.bodyMedium)
        i.rating?.let { Text(stringResource(R.string.rating_value, it.toString()) + (i.reviewCount?.let { n -> " · " + stringResource(R.string.public_reviews, n) } ?: "")) }
        Block(stringResource(R.string.address)) { Text(i.address) }
        Block(stringResource(R.string.phone)) { Text(i.phone) }
        Block(stringResource(R.string.opening_hours)) { HoursText(i) }
        Block(stringResource(R.string.food_categories)) { Text(stringResource(R.string.food_categories_value)) }
        Block(stringResource(R.string.dine_in)) { Text(i.dineInInfo ?: stringResource(R.string.dine_in_default)) }
        i.parkingInfo?.let { Block(stringResource(R.string.parking)) { Text(it) } }
        if (i.deliveryEnabled) Block(stringResource(R.string.delivery)) { Text(i.deliveryAreaNote ?: stringResource(R.string.delivery_available)) }
        i.supportEmail?.let { Block(stringResource(R.string.customer_support)) { Text(it) } }
        i.fssaiNumber?.let { Block(stringResource(R.string.fssai)) { Text(it) } }
        i.gstin?.let { Block("GSTIN") { Text(it) } }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button({ vm.track(Analytics.CALL); Intents.call(context, i.phone) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.call_restaurant)) }
        androidx.compose.material3.OutlinedButton({ vm.track(Analytics.DIRECTIONS); Intents.directions(context, i) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.get_directions)) }
        androidx.compose.material3.OutlinedButton({ vm.track(Analytics.SHARE); Intents.share(context, i) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_restaurant)) }
        androidx.compose.material3.OutlinedButton(onMenu, Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_menu)) }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    content()
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
        Text(stringResource(R.string.hours_unverified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
