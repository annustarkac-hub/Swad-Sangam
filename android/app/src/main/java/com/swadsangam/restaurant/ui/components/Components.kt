package com.swadsangam.restaurant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.FoodType
import com.swadsangam.restaurant.domain.MenuItem
import com.swadsangam.restaurant.ui.theme.NonVegBrown
import com.swadsangam.restaurant.ui.theme.PriceStyle
import com.swadsangam.restaurant.ui.theme.SuccessGreen

/** Standard Indian veg (green dot) / non-veg (brown triangle) marks — visually distinct (PRD §2.3). */
@Composable
fun FoodTypeMark(type: FoodType, size: Dp = 16.dp) {
    val color = if (type == FoodType.VEG) SuccessGreen else NonVegBrown
    val label = stringResource(if (type == FoodType.VEG) R.string.veg else R.string.non_veg)
    Box(
        Modifier.size(size).border(1.5.dp, color, RoundedCornerShape(2.dp)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.55f)) {
            if (type == FoodType.VEG) {
                drawCircle(color)
            } else {
                val p = Path().apply {
                    moveTo(this@Canvas.size.width / 2, 0f)
                    lineTo(this@Canvas.size.width, this@Canvas.size.height)
                    lineTo(0f, this@Canvas.size.height)
                    close()
                }
                drawPath(p, color)
            }
        }
    }
}

@Composable
fun Price(paise: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(Money.format(paise), style = PriceStyle, color = color, modifier = modifier)
}

@Composable
fun DishImage(url: String?, modifier: Modifier) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
    if (url.isNullOrBlank()) {
        Box(modifier) { placeholder() }
    } else {
        // Never crashes if an image fails to load (FR-01)
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier,
            loading = { placeholder() }, error = { placeholder() },
        )
    }
}

@Composable
fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, max: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(quantity - 1) }, Modifier.size(48.dp)) {
            Icon(Icons.Default.Remove, stringResource(R.string.decrease_quantity), tint = MaterialTheme.colorScheme.primary)
        }
        Text("$quantity", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(quantity + 1) }, enabled = quantity < max, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.increase_quantity), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Menu row: name, veg mark, description, price, availability, add button. */
@Composable
fun DishRow(
    item: MenuItem,
    hindi: Boolean,
    cartQuantity: Int,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodTypeMark(item.foodType)
                if (item.isPopular) {
                    Spacer(Modifier.width(8.dp))
                    Tag(stringResource(R.string.popular))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(item.displayName(hindi), style = MaterialTheme.typography.titleMedium)
            Price(item.pricePaise)
            item.displayDescription(hindi)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!item.isAvailable) {
                Text(stringResource(R.string.currently_unavailable), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DishImage(item.imageUrl, Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(6.dp))
            if (item.isAvailable) {
                OutlinedButton(onClick = if (item.options.isEmpty()) onAdd else onOpen, modifier = Modifier.height(40.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                    Text(if (cartQuantity > 0) stringResource(R.string.add_more, cartQuantity) else stringResource(R.string.add))
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, isError: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null, offline: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (offline) Icons.Default.CloudOff else Icons.Default.Restaurant, null,
            Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        body?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
