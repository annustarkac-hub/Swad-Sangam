package com.swadsangam.restaurant.ui.components

import com.swadsangam.restaurant.ui.theme.Elevation

import com.swadsangam.restaurant.ui.theme.Border

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swadsangam.restaurant.ui.theme.AppTheme
import com.swadsangam.restaurant.ui.theme.IconSize
import com.swadsangam.restaurant.ui.theme.Radius
import com.swadsangam.restaurant.ui.theme.Size
import com.swadsangam.restaurant.ui.theme.Spacing

/*
 * Swad Sangam component library. Every screen builds from these so buttons, fields, cards, banners,
 * chips and list rows look and behave the same everywhere.
 */

enum class ButtonVariant { Primary, Secondary, Tonal, Text, Danger }

/**
 * The only button style in the app.
 * Primary = main action of a screen (one per screen); Secondary = outlined alternative;
 * Tonal = supporting action on cards; Text = low-emphasis inline action; Danger = destructive.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    large: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val height = if (large) Size.buttonLarge else Size.buttonMedium
    val shape = RoundedCornerShape(Radius.m)
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(IconSize.m), strokeWidth = Border.progress, color = androidx.compose.material3.LocalContentColor.current)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(IconSize.m)); Spacer(Modifier.width(Spacing.s)) }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
    val m = modifier.heightIn(min = maxOf(height, Size.touch))
    val on = enabled && !loading
    when (variant) {
        ButtonVariant.Primary -> Button(onClick, m, on, shape = shape, contentPadding = PaddingValues(horizontal = Spacing.xl), content = content)
        ButtonVariant.Danger -> Button(onClick, m, on, shape = shape, contentPadding = PaddingValues(horizontal = Spacing.xl),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), content = content)
        ButtonVariant.Secondary -> OutlinedButton(onClick, m, on, shape = shape, contentPadding = PaddingValues(horizontal = Spacing.xl),
            border = BorderStroke(Border.hairline, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), content = content)
        ButtonVariant.Tonal -> FilledTonalButton(onClick, m, on, shape = shape, contentPadding = PaddingValues(horizontal = Spacing.l), content = content)
        ButtonVariant.Text -> TextButton(onClick, m, on, shape = shape, contentPadding = PaddingValues(horizontal = Spacing.m), content = content)
    }
}

/** Labelled text field with error text under the field (never placeholder-only labels). */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = singleLine, minLines = minLines, enabled = enabled,
        isError = error != null, keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        prefix = prefix?.let { { Text(it) } },
        supportingText = (error ?: supporting)?.let { msg -> { Text(msg) } },
        shape = RoundedCornerShape(Radius.m),
    )
}

/** Standard content card: 20 dp corners, surface colour, low elevation, consistent inner padding. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.l),
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
    val shape = RoundedCornerShape(Radius.l)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

enum class Tone { Neutral, Brand, Success, Warning, Error }

private data class ToneColors(val container: Color, val content: Color)

@Composable
private fun toneColors(tone: Tone): ToneColors = when (tone) {
    Tone.Neutral -> ToneColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    Tone.Brand -> ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    Tone.Success -> ToneColors(AppTheme.colors.successContainer, AppTheme.colors.onSuccessContainer)
    Tone.Warning -> ToneColors(AppTheme.colors.warningContainer, AppTheme.colors.onWarningContainer)
    Tone.Error -> ToneColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
}

/** Inline message banner (closed, offline, errors, notes) with an optional action. */
@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Warning,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = toneColors(tone)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m), color = c.container, contentColor = c.content) {
        Row(Modifier.padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s).heightIn(min = Size.chip),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(IconSize.m)); Spacer(Modifier.width(Spacing.m)) }
            Text(text, Modifier.weight(1f).padding(vertical = Spacing.xs), style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                TextButton(onAction, Modifier.heightIn(min = Size.touch)) { Text(action, color = c.content, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Small pill label (status, "Popular", category). */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral) {
    val c = toneColors(tone)
    Surface(modifier, shape = RoundedCornerShape(50), color = c.container, contentColor = c.content) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.s + Spacing.xxs, vertical = Spacing.xxs + Spacing.xxs / 2))
    }
}

/** One tappable settings/info row: leading icon, title, optional subtitle, chevron. 56 dp min height. */
@Composable
fun ListRow(title: String, icon: ImageVector, onClick: () -> Unit, subtitle: String? = null, showChevron: Boolean = true) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = {
            Surface(shape = RoundedCornerShape(Radius.s), color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(Size.iconContainer)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(IconSize.m)) }
            }
        },
        trailingContent = if (showChevron) { { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.listRow).clickableRow(onClick),
    )
}

/** Sticky bottom area for a screen's primary action (cart, checkout, dish). Respects the gesture bar. */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(tonalElevation = Elevation.mid, shadowElevation = Elevation.barShadow, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
    }
}

/** Two-column label/value line used in bills and summaries. */
@Composable
fun KeyValueRow(label: String, value: String, emphasize: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold, color = valueColor)
    }
}

/** Full-area loading state with a short label (never a blank screen). */
@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.size(Spacing.l))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Section divider with the standard gutter. */
@Composable
fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/** Pill-shaped search field (filled, no outline) with clear + optional trailing action. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearLabel: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().heightIn(min = Size.touch),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) androidx.compose.material3.IconButton({ onValueChange("") }) {
                    Icon(Icons.Default.Clear, clearLabel)
                }
                trailing?.invoke()
            }
        },
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
