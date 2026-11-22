package com.swadsangam.restaurant.ui.home

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import com.swadsangam.restaurant.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.data.repo.Analytics
import com.swadsangam.restaurant.data.repo.SyncState
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.ui.components.DishImage
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.components.FoodTypeMark
import com.swadsangam.restaurant.ui.components.InfoBanner
import com.swadsangam.restaurant.ui.components.Price
import com.swadsangam.restaurant.ui.components.SectionHeader
import com.swadsangam.restaurant.ui.components.Tag
import com.swadsangam.restaurant.ui.more.HoursText
import com.swadsangam.restaurant.util.Intents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hindi: Boolean,
    onOpenMenu: (category: String?, search: Boolean) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenInfo: () -> Unit,
    onOpenOrders: () -> Unit,
    vm: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val info = s.info
    val maxQty = info?.maxItemQuantity ?: 20
    val titles = listOf(
        stringResource(R.string.popular_dishes),
        stringResource(R.string.budget_meals, com.swadsangam.restaurant.core.Money.format(info?.budgetThresholdPaise ?: 10000)),
        stringResource(R.string.explore_thalis),
        stringResource(R.string.breakfast_snacks),
        stringResource(R.string.vegetarian),
        stringResource(R.string.non_vegetarian),
    )

    PullToRefreshBox(isRefreshing = s.sync is SyncState.Loading, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // 1–2. Logo, name, location + directions shortcut
            item {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(Dimens.radiusM), color = Color.White, modifier = Modifier.size(52.dp)) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.res.painterResource(R.drawable.logo_mark), contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text(stringResource(R.string.home_tagline), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        // Pure Veg Mode (remembered on this phone)
                        Column(
                            Modifier.toggleable(value = s.pureVeg, role = Role.Switch, onValueChange = { vm.setPureVeg(it) }).padding(start = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Switch(checked = s.pureVeg, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = com.swadsangam.restaurant.ui.theme.SuccessGreen,
                                    checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.25f),
                                    uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.White.copy(alpha = 0.6f)))
                            Text(stringResource(R.string.pure_veg), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.heightIn(min = 48.dp).clickable { info?.let { vm.track(Analytics.DIRECTIONS); Intents.directions(context, it) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.location_short), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.get_directions), color = Color.White, style = MaterialTheme.typography.labelLarge,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 3. Search bar (opens the menu with search focused)
                    Surface(
                        shape = RoundedCornerShape(Dimens.radiusM), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().height(Dimens.touch).clickable { onOpenMenu(null, true) },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Status banners
            item {
                val st = s.status
                when {
                    info?.temporarilyClosed == true ->
                        InfoBanner(info.closureMessage ?: stringResource(R.string.temporarily_closed), isError = true)
                    st != null && !st.isOpenNow -> InfoBanner(stringResource(R.string.closed_now))
                    info != null && (!info.acceptingOrders || !info.anyOrderTypeEnabled) ->
                        InfoBanner(stringResource(R.string.ordering_unavailable_call), action = stringResource(R.string.call),
                            onAction = { vm.track(Analytics.CALL); Intents.call(context, info.phone) })
                }
                info?.announcement?.takeIf { it.isNotBlank() }?.let { InfoBanner(it) }
                if (s.sync is SyncState.Failed && s.hasMenu) {
                    InfoBanner(stringResource(R.string.showing_saved_menu), action = stringResource(R.string.retry), onAction = { vm.refresh() })
                }
            }

            if (!s.hasMenu) {
                item {
                    when (val sync = s.sync) {
                        is SyncState.Failed -> EmptyState(
                            title = stringResource(if (sync.offline) R.string.no_internet else R.string.menu_load_failed),
                            body = stringResource(R.string.call_to_order_body),
                            actionLabel = stringResource(R.string.retry), onAction = { vm.refresh() }, offline = sync.offline,
                        )
                        else -> EmptyState(stringResource(R.string.loading_menu))
                    }
                    if (info != null) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.track(Analytics.CALL); Intents.call(context, info.phone) }, Modifier.weight(1f)) {
                                Text(stringResource(R.string.call_restaurant))
                            }
                        }
                    }
                }
            }

            // 4. Hero banner featuring an actual Swad Sangam dish (only shown with a real photo)
            s.heroItem?.let { hero ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp).height(180.dp).clickable { onOpenMenu("thalis", false) },
                        shape = RoundedCornerShape(Dimens.radiusL),
                    ) {
                        Box {
                            DishImage(hero.imageUrl, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text(stringResource(R.string.explore_thalis), style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text(stringResource(R.string.fresh_budget), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Offers / promotions
            if (s.offers.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.offers, key = { it.id }) { o ->
                            Card(Modifier.width(280.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Column(Modifier.padding(12.dp)) {
                                    if (o.imageUrl != null) {
                                        DishImage(o.imageUrl, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    Text(o.title, style = MaterialTheme.typography.titleMedium)
                                    o.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    o.couponCode?.let { Spacer(Modifier.height(4.dp)); Tag(stringResource(R.string.use_code, it)) }
                                }
                            }
                        }
                    }
                }
            }

            // Category chips
            if (s.categories.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.categories, key = { it.id }) { c ->
                            AssistChip(onClick = { onOpenMenu(c.id, false) },
                                label = { Text(if (hindi && !c.nameHi.isNullOrBlank()) c.nameHi else c.name) })
                        }
                    }
                }
            }

            // 5–9. Dish sections
            section(titles[0], s.popular, hindi, onOpenItem) { vm.quickAdd(it, maxQty) }
            section(titles[1], s.budget, hindi, onOpenItem) { vm.quickAdd(it, maxQty) }
            section(titles[2], s.thalis, hindi, onOpenItem, onMore = { onOpenMenu("thalis", false) }) { vm.quickAdd(it, maxQty) }
            section(titles[3], s.breakfastSnacks, hindi, onOpenItem) { vm.quickAdd(it, maxQty) }
            section(titles[4], s.veg, hindi, onOpenItem) { vm.quickAdd(it, maxQty) }
            section(titles[5], s.nonVeg, hindi, onOpenItem) { vm.quickAdd(it, maxQty) }

            // 10. Customer reviews — verified public rating only, no fabricated reviews (FR-12)
            if (info?.rating != null) {
                item {
                    SectionHeader(stringResource(R.string.customer_reviews))
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.rating_value, info.rating.toString()), style = MaterialTheme.typography.titleMedium)
                                info.reviewCount?.let { Text(stringResource(R.string.public_reviews, it), style = MaterialTheme.typography.bodySmall) }
                            }
                            info.reviewsUrl?.let { url ->
                                OutlinedButton(onClick = { Intents.openUrl(context, url) }) { Text(stringResource(R.string.read_reviews)) }
                            }
                        }
                    }
                }
            }

            // 11–12. Timings + contact & ordering buttons
            if (info != null) {
                item {
                    SectionHeader(stringResource(R.string.timings), stringResource(R.string.more_info), onOpenInfo)
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            HoursText(info)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.track(Analytics.CALL); Intents.call(context, info.phone) }, Modifier.weight(1f)) {
                                    Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.call))
                                }
                                OutlinedButton(onClick = { vm.track(Analytics.DIRECTIONS); Intents.directions(context, info) }, Modifier.weight(1f)) {
                                    Icon(Icons.Default.Directions, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.directions))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onOpenMenu(null, false) }, Modifier.fillMaxWidth()) {
                                Text(stringResource(if (info.acceptingOrders && info.anyOrderTypeEnabled) R.string.order_now else R.string.view_menu))
                            }
                            OutlinedButton(onClick = onOpenOrders, Modifier.fillMaxWidth()) { Text(stringResource(R.string.my_orders)) }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<MenuItem>,
    hindi: Boolean,
    onOpenItem: (String) -> Unit,
    onMore: (() -> Unit)? = null,
    onAdd: (MenuItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "section-$title") {
        Column {
            SectionHeader(title, if (onMore != null) stringResource(R.string.see_all) else null, onMore)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item -> DishCard(item, hindi, { onOpenItem(item.id) }, { onAdd(item) }) }
            }
        }
    }
}

@Composable
private fun DishCard(item: MenuItem, hindi: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    Card(Modifier.width(164.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(Dimens.radiusM)) {
        DishImage(item.imageUrl, Modifier.fillMaxWidth().height(110.dp))
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodTypeMark(item.foodType, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(item.displayName(hindi), style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.variantGroups.isEmpty()) com.swadsangam.restaurant.core.Money.format(item.pricePaise)
                    else stringResource(R.string.from_price, com.swadsangam.restaurant.core.Money.format(item.pricePaise)),
                    style = com.swadsangam.restaurant.ui.theme.PriceStyle, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = if (item.options.isEmpty()) onAdd else onOpen,
                    modifier = Modifier.heightIn(min = Dimens.touch)) { Text(stringResource(R.string.add)) }
            }
        }
    }
}
