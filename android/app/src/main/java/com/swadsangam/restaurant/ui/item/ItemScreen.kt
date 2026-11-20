package com.swadsangam.restaurant.ui.item

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
        if (s.loaded) EmptyState(stringResource(R.string.item_not_found))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DishImage(item.imageUrl, Modifier.fillMaxWidth().height(240.dp))
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodTypeMark(item.foodType, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (item.foodType == FoodType.VEG) R.string.veg else R.string.non_veg), style = MaterialTheme.typography.labelMedium)
                    s.categoryName?.let { Spacer(Modifier.width(8.dp)); Tag(it) }
                    if (item.isPopular) { Spacer(Modifier.width(8.dp)); Tag(stringResource(R.string.popular)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(item.displayName(hindi), style = MaterialTheme.typography.headlineSmall)
                Price(item.pricePaise)
                item.displayDescription(hindi)?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                item.portionInfo?.let { Detail(stringResource(R.string.portion), it) }
                item.preparationMinutes?.let { Detail(stringResource(R.string.prep_time), stringResource(R.string.minutes, it)) }
                Detail(stringResource(R.string.allergens), item.allergens ?: stringResource(R.string.allergens_ask))
                if (!item.isAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.currently_unavailable), color = MaterialTheme.colorScheme.error)
                }
                if (item.options.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.options), style = MaterialTheme.typography.titleMedium)
                    item.options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = o.isAvailable) { vm.toggleOption(o.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = o.id in s.selected, onCheckedChange = { vm.toggleOption(o.id) }, enabled = o.isAvailable)
                            Text(o.name, Modifier.weight(1f))
                            Text(if (o.pricePaise > 0) "+" + Money.format(o.pricePaise) else stringResource(R.string.free))
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (item.isAvailable) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(s.quantity, vm::setQuantity, s.maxQuantity)
                Spacer(Modifier.width(12.dp))
                Button(onClick = { vm.addToCart(onAdded) }, Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.add_to_cart_price, Money.format(s.totalPaise)))
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Row {
        Text("$label: ", style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
