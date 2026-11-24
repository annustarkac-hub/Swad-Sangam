package com.swadsangam.restaurant.ui.menu

import com.swadsangam.restaurant.ui.components.StatusBanner
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.SectionHeader
import com.swadsangam.restaurant.ui.components.SearchField
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.ui.components.DishRow
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.components.InfoBanner

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    hindi: Boolean,
    focusSearch: Boolean,
    tableNumber: String?,
    onOpenItem: (String) -> Unit,
    vm: MenuViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pureVeg by vm.pureVeg.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val f = s.filters
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var jumpSheet by remember { mutableStateOf(false) }
    // A non-veg filter can never match in Pure Veg mode — reset it instead of showing an unexplained empty list
    LaunchedEffect(pureVeg) { if (pureVeg && f.food == FoodFilter.NON_VEG) vm.update { it.copy(food = FoodFilter.ALL) } }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = f.query, onValueChange = { q -> vm.update { it.copy(query = q) } },
            placeholder = stringResource(R.string.search_hint), clearLabel = stringResource(R.string.clear),
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s).focusRequester(focus),
            trailing = {
                IconButton({ showFilters = !showFilters }) {
                    Icon(Icons.Default.Tune, stringResource(R.string.filters),
                        tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        if (tableNumber != null) InfoBanner(stringResource(R.string.ordering_for_table, tableNumber), tone = Tone.Brand)
        if (pureVeg) {
            StatusBanner(stringResource(R.string.pure_veg_on), Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs), tone = Tone.Success,
                action = stringResource(R.string.turn_off), onAction = { vm.setPureVeg(false) })
        }

        // Category filter
        LazyRow(contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item {
                FilterChip(selected = f.categoryId == null, onClick = { vm.update { it.copy(categoryId = null) } },
                    label = { Text(stringResource(R.string.all)) }, modifier = Modifier.heightIn(min = Size.touch))
            }
            items(s.categories, key = { it.id }) { c ->
                FilterChip(selected = f.categoryId == c.id, onClick = { vm.update { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) } },
                    label = { Text(if (hindi && !c.nameHi.isNullOrBlank()) c.nameHi else c.name) }, modifier = Modifier.heightIn(min = Size.touch))
            }
        }

        if (showFilters) {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(f.food == FoodFilter.VEG, { vm.update { it.copy(food = if (it.food == FoodFilter.VEG) FoodFilter.ALL else FoodFilter.VEG) } },
                        label = { Text(stringResource(R.string.veg_only)) })
                    if (!pureVeg) {
                        FilterChip(f.food == FoodFilter.NON_VEG, { vm.update { it.copy(food = if (it.food == FoodFilter.NON_VEG) FoodFilter.ALL else FoodFilter.NON_VEG) } },
                            label = { Text(stringResource(R.string.non_veg)) })
                    }
                    Column {
                        FilterChip(f.sort != SortOrder.DEFAULT, { sortMenu = true }, label = {
                            Text(when (f.sort) {
                                SortOrder.DEFAULT -> stringResource(R.string.sort)
                                SortOrder.PRICE_LOW_HIGH -> stringResource(R.string.price_low_high)
                                SortOrder.PRICE_HIGH_LOW -> stringResource(R.string.price_high_low)
                            })
                        })
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortOrder.entries.forEach { o ->
                                DropdownMenuItem(text = {
                                    Text(when (o) {
                                        SortOrder.DEFAULT -> stringResource(R.string.sort_default)
                                        SortOrder.PRICE_LOW_HIGH -> stringResource(R.string.price_low_high)
                                        SortOrder.PRICE_HIGH_LOW -> stringResource(R.string.price_high_low)
                                    })
                                }, onClick = { vm.update { it.copy(sort = o) }; sortMenu = false })
                            }
                        }
                    }
                }
                if (s.priceCeilingPaise > 0) {
                    val ceiling = s.priceCeilingPaise
                    val current = f.maxPricePaise ?: ceiling
                    Text(stringResource(R.string.max_price, Money.format(current)), style = MaterialTheme.typography.labelLarge, color = secondaryText)
                    Slider(
                        value = current.toFloat(), valueRange = 0f..ceiling.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(maxPricePaise = ((v / 1000).toInt() * 1000).coerceAtLeast(1000).takeIf { p -> p < ceiling }) } },
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(refreshing, { vm.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = Size.touch * 3)) {
            if (s.loaded && s.results.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_results),
                        body = stringResource(R.string.no_results_body),
                        actionLabel = stringResource(R.string.clear_filters),
                        onAction = { vm.update { MenuFilters() } },
                    )
                }
            }
            if (s.groups.isNotEmpty()) {
                s.groups.forEach { (cat, items) ->
                    item(key = "h-${cat.id}") {
                        SectionHeader(if (hindi && !cat.nameHi.isNullOrBlank()) cat.nameHi else cat.name)
                    }
                    items(items, key = { it.id }) { item ->
                        DishRow(item, hindi, s.cartQuantities[item.id] ?: 0, { onOpenItem(item.id) }, { vm.quickAdd(item) },
                            onSetQuantity = { q -> vm.setQuantity(item, q) }, maxQuantity = s.info?.maxItemQuantity ?: 20)
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.gutter), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                items(s.results, key = { it.id }) { item ->
                    DishRow(item, hindi, s.cartQuantities[item.id] ?: 0, { onOpenItem(item.id) }, { vm.quickAdd(item) },
                        onSetQuantity = { q -> vm.setQuantity(item, q) }, maxQuantity = s.info?.maxItemQuantity ?: 20)
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.gutter), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        }
        // Quick jump between the 22 categories (only when the menu is shown by category)
        if (s.groups.size > 1) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { jumpSheet = true },
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                text = { Text(stringResource(R.string.menu_jump)) },
                containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
        }
    }

    if (jumpSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { jumpSheet = false }) {
            Text(stringResource(R.string.jump_to_category), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                var index = 0
                val starts = s.groups.map { g -> index.also { index += 1 + g.second.size } }
                itemsIndexed(s.groups) { i, (cat, list) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable {
                        jumpSheet = false
                        scope.launch { listState.animateScrollToItem(starts[i]) }
                    }.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hindi && !cat.nameHi.isNullOrBlank()) cat.nameHi else cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(list.size.toString(), style = MaterialTheme.typography.labelLarge, color = secondaryText)
                    }
                }
            }
        }
    }
}
