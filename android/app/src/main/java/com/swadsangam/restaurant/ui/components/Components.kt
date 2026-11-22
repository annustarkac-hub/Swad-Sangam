package com.swadsangam.restaurant.ui.components

import com.swadsangam.restaurant.ui.theme.Border

import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import com.swadsangam.restaurant.ui.theme.AppTheme
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Radius
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.Spacing
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
import androidx.compose.foundation.layout.heightIn
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
import com.swadsangam.restaurant.ui.theme.PriceStyle

/** Standard Indian veg (green dot) / non-veg (brown triangle) marks — visually distinct (PRD §2.3). */
@Composable
fun FoodTypeMark(type: FoodType, size: Dp = Size.vegMark) {
    val color = if (type == FoodType.VEG) AppTheme.colors.veg else AppTheme.colors.nonVeg
    val label = stringResource(if (type == FoodType.VEG) R.string.veg else R.string.non_veg)
    Box(
        // Indian FSSAI-style mark: square outline with a dot (veg) or triangle (non-veg)
        Modifier.size(size).border(Border.mark, color, RoundedCornerShape(Radius.xxs)).semantics { contentDescription = label },
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

/** "Half ₹120 · Full ₹199" for dishes with sizes, otherwise the base price. */
@Composable
fun priceLabel(item: MenuItem): String {
    val sizes = item.variantGroups["size"].orEmpty()
    if (sizes.isEmpty()) return Money.format(item.pricePaise)
    val base = item.variantBaseLabel ?: stringResource(R.string.variant_regular)
    return (listOf("$base ${Money.format(item.pricePaise)}") + sizes.map { "${it.name} ${Money.format(item.pricePaise + it.pricePaise)}" })
        .joinToString(" · ")
}

@Composable
fun Price(paise: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(Money.format(paise), style = PriceStyle, color = color, modifier = modifier)
}

@Composable
fun DishImage(url: String?, modifier: Modifier) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Restaurant, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.outline)
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
        modifier.border(Border.hairline, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(quantity - 1) }, Modifier.size(Size.touch)) {
            Icon(Icons.Default.Remove, stringResource(R.string.decrease_quantity), tint = MaterialTheme.colorScheme.primary)
        }
        Text("$quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(Spacing.xl), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(quantity + 1) }, enabled = quantity < max, modifier = Modifier.size(Size.touch)) {
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
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodTypeMark(item.foodType)
                if (item.isPopular) {
                    Spacer(Modifier.width(Spacing.s))
                    Tag(stringResource(R.string.popular))
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(item.displayName(hindi), style = MaterialTheme.typography.titleMedium)
            Text(priceLabel(item), style = PriceStyle)
            item.displayDescription(hindi)?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!item.isAvailable) {
                Text(stringResource(R.string.currently_unavailable), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DishImage(item.imageUrl, Modifier.size(Size.thumbnail).clip(RoundedCornerShape(Radius.m)))
            Spacer(Modifier.height(Spacing.s))
            if (item.isAvailable) {
                AppButton(
                    text = if (cartQuantity > 0) stringResource(R.string.add_more, cartQuantity) else stringResource(R.string.add),
                    onClick = if (item.options.isEmpty()) onAdd else onOpen,
                    variant = ButtonVariant.Secondary, modifier = Modifier.width(Size.thumbnail),
                )
            }
        }
    }
}

/** Small pill label. Kept for existing call sites; see [StatusChip]. */
@Composable
fun Tag(text: String, tone: Tone = Tone.Brand) = StatusChip(text, tone = tone)

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.xl, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            AppButton(action, onAction, variant = ButtonVariant.Text)
        }
    }
}

/** Screen-level banner with the standard gutter. isError → error tone, otherwise warning tone. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, isError: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null,
               tone: Tone = if (isError) Tone.Error else Tone.Warning) {
    StatusBanner(text, modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs), tone = tone,
        icon = if (tone == Tone.Error) Icons.Default.ErrorOutline else Icons.Default.Info, action = action, onAction = onAction)
}

/** Empty / offline / not-found state: illustration icon, title, body and one action. */
@Composable
fun EmptyState(title: String, body: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null, offline: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(IconSize.hero + Spacing.xl)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (offline) Icons.Default.CloudOff else Icons.Default.Restaurant, null, Modifier.size(IconSize.hero / 2),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        body?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xl))
            AppButton(actionLabel, onAction)
        }
    }
}

/** Corner radius of the veg / non-veg mark (FSSAI-style square). */
