package com.swadsangam.restaurant.ui.item

import com.swadsangam.restaurant.ui.theme.secondaryText
import com.swadsangam.restaurant.ui.theme.PriceStyle
import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusBanner
import com.swadsangam.restaurant.ui.components.LoadingState
import com.swadsangam.restaurant.ui.components.BottomActionBar
import com.swadsangam.restaurant.ui.components.AppButton
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Spacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import com.swadsangam.restaurant.ui.components.priceLabel
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.ui.components.DishImage
import com.swadsangam.restaurant.ui.components.EmptyState
import com.swadsangam.restaurant.ui.components.FoodTypeMark
import com.swadsangam.restaurant.ui.components.Price
import com.swadsangam.restaurant.ui.components.QuantityStepper
import com.swadsangam.restaurant.ui.components.Tag

@Composable
fun ItemScreen(hindi: Boolean, onAdded: () -> Unit, vm: ItemViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val item = s.item
    if (item == null) {
        if (s.loaded) EmptyState(stringResource(R.string.item_not_found)) else LoadingState(stringResource(R.string.loading_menu))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DishImage(item.imageUrl, Modifier.fillMaxWidth().height(Size.heroImage))
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodTypeMark(item.foodType, IconSize.s)
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(if (item.foodType == FoodType.VEG) R.string.veg else R.string.non_veg), style = MaterialTheme.typography.labelMedium)
                    s.categoryName?.let { Spacer(Modifier.width(Spacing.s)); Tag(it) }
                    if (item.isPopular) { Spacer(Modifier.width(Spacing.s)); Tag(stringResource(R.string.popular)) }
                }
                Spacer(Modifier.height(Spacing.s))
                Text(item.displayName(hindi), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.xs))
                Text(priceLabel(item), style = PriceStyle, color = MaterialTheme.colorScheme.primary)
                item.displayDescription(hindi)?.let {
                    Spacer(Modifier.height(Spacing.s)); Text(it, style = MaterialTheme.typography.bodyLarge, color = secondaryText)
                }
                item.portionInfo?.let { Detail(stringResource(R.string.portion), it) }
                item.preparationMinutes?.let { Detail(stringResource(R.string.prep_time), stringResource(R.string.minutes, it)) }
                Detail(stringResource(R.string.allergens), item.allergens ?: stringResource(R.string.allergens_ask))
                if (!item.isAvailable) {
                    Spacer(Modifier.height(Spacing.m))
                    StatusBanner(stringResource(R.string.currently_unavailable), tone = Tone.Error)
                }
                // Alternatives (Half / Full, Dry / Gravy): one choice per group
                item.variantGroups.forEach { (group, opts) ->
                    Spacer(Modifier.height(Spacing.xl))
                    Text(stringResource(R.string.choose_one), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    val chosen = opts.firstOrNull { it.id in s.selected }
                    VariantRow(item.variantBaseLabel ?: stringResource(R.string.variant_regular), item.pricePaise, chosen == null, true) {
                        vm.chooseVariant(group, null)
                    }
                    opts.forEach { o ->
                        VariantRow(o.name, item.pricePaise + o.pricePaise, chosen?.id == o.id, o.isAvailable) { vm.chooseVariant(group, o.id) }
                    }
                }
                // Add-ons: tick any
                if (item.addOns.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xl))
                    Text(stringResource(if (item.variantGroups.isEmpty()) R.string.options else R.string.add_ons), style = MaterialTheme.typography.titleMedium)
                    item.addOns.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Size.touch).clickable(enabled = o.isAvailable) { vm.toggleOption(o.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = o.id in s.selected, onCheckedChange = { vm.toggleOption(o.id) }, enabled = o.isAvailable)
                            Text(o.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(if (o.pricePaise > 0) "+" + Money.format(o.pricePaise) else stringResource(R.string.free), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (item.isAvailable) {
            BottomActionBar {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(s.quantity, vm::setQuantity, s.maxQuantity)
                    Spacer(Modifier.width(Spacing.m))
                    AppButton(stringResource(R.string.add_to_cart_price, Money.format(s.totalPaise)), { vm.addToCart(onAdded) },
                        Modifier.weight(1f), large = true)
                }
            }
        }
    }
}

/** One radio row: label, full price, selected state. */
@Composable
private fun VariantRow(label: String, pricePaise: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Size.touch)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(Spacing.s))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(Money.format(pricePaise), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Spacer(Modifier.height(Spacing.s))
    Row {
        Text("$label: ", style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
    }
}
