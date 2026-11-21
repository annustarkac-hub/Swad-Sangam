package com.swadsangam.restaurant.ui.menu

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

@Composable
fun MenuScreen(
    hindi: Boolean,
    focusSearch: Boolean,
    tableNumber: String?,
    onOpenItem: (String) -> Unit,
    vm: MenuViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val f = s.filters
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = f.query,
            onValueChange = { q -> vm.update { it.copy(query = q) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row {
                    if (f.query.isNotEmpty()) IconButton({ vm.update { it.copy(query = "") } }) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) }
                    IconButton({ showFilters = !showFilters }) { Icon(Icons.Default.Tune, stringResource(R.string.filters)) }
                }
            },
            singleLine = true,
        )
        if (tableNumber != null) InfoBanner(stringResource(R.string.ordering_for_table, tableNumber))

        // Category filter
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = f.categoryId == null, onClick = { vm.update { it.copy(categoryId = null) } },
                    label = { Text(stringResource(R.string.all)) })
            }
            items(s.categories, key = { it.id }) { c ->
                FilterChip(selected = f.categoryId == c.id, onClick = { vm.update { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) } },
                    label = { Text(if (hindi && !c.nameHi.isNullOrBlank()) c.nameHi else c.name) })
            }
        }

        if (showFilters) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(f.food == FoodFilter.VEG, { vm.update { it.copy(food = if (it.food == FoodFilter.VEG) FoodFilter.ALL else FoodFilter.VEG) } },
                        label = { Text(stringResource(R.string.veg_only)) })
                    FilterChip(f.food == FoodFilter.NON_VEG, { vm.update { it.copy(food = if (it.food == FoodFilter.NON_VEG) FoodFilter.ALL else FoodFilter.NON_VEG) } },
                        label = { Text(stringResource(R.string.non_veg)) })
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
                    Text(stringResource(R.string.max_price, Money.format(current)), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = current.toFloat(), valueRange = 0f..ceiling.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(maxPricePaise = ((v / 1000).toInt() * 1000).coerceAtLeast(1000).takeIf { p -> p < ceiling }) } },
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                        Text(if (hindi && !cat.nameHi.isNullOrBlank()) cat.nameHi else cat.name,
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    }
                    items(items, key = { it.id }) { item ->
                        DishRow(item, hindi, s.cartQuantities[item.id] ?: 0, { onOpenItem(item.id) }, { vm.quickAdd(item) })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            } else {
                items(s.results, key = { it.id }) { item ->
                    DishRow(item, hindi, s.cartQuantities[item.id] ?: 0, { onOpenItem(item.id) }, { vm.quickAdd(item) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
