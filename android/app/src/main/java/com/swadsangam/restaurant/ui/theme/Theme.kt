package com.swadsangam.restaurant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand palette taken from the Swad Sangam signboard (orange on navy) — same as the website.
// Buttons use the deeper orange so white text passes WCAG AA contrast (4.7:1).
val BrandOrange = Color(0xFFC94A07)
val BrandOrangeBright = Color(0xFFE8590C)
val Navy = Color(0xFF1F2A44)
val Saffron = Color(0xFFF59F00)
val Cream = Color(0xFFFFF8F1)
val TextInk = Color(0xFF1C1917)
val SuccessGreen = Color(0xFF1F8A4C)
val ErrorRed = Color(0xFFC8392F)
val NonVegBrown = Color(0xFF8B3A1A)

private val Light = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D1),
    onPrimaryContainer = Color(0xFF4A1500),
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEFD6),
    onSecondaryContainer = Color(0xFF3D2A00),
    tertiary = Saffron,
    onTertiary = TextInk,
    background = Cream,
    onBackground = TextInk,
    surface = Color.White,
    onSurface = TextInk,
    surfaceVariant = Color(0xFFF4ECE4),
    onSurfaceVariant = Color(0xFF5F564F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF7),
    surfaceContainer = Color(0xFFFFF4EA),
    surfaceContainerHigh = Color(0xFFF9EDE2),
    surfaceContainerHighest = Color(0xFFF3E6DA),
    outline = Color(0xFFCDBFB3),
    outlineVariant = Color(0xFFE7DCD2),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5C1210),
)

// Every role is set so no Material default (purple) leaks into dark mode.
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF552000),
    primaryContainer = Color(0xFF793000),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFB9C6E8),
    onSecondary = Color(0xFF23304A),
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = Color(0xFFFFE3B5),
    tertiary = Saffron,
    onTertiary = Color(0xFF2B1C00),
    background = Color(0xFF141110),
    onBackground = Color(0xFFF2ECE8),
    surface = Color(0xFF1C1918),
    onSurface = Color(0xFFF2ECE8),
    surfaceVariant = Color(0xFF2C2624),
    onSurfaceVariant = Color(0xFFD3C7BF),
    surfaceContainerLowest = Color(0xFF0F0D0C),
    surfaceContainerLow = Color(0xFF1A1716),
    surfaceContainer = Color(0xFF211D1C),
    surfaceContainerHigh = Color(0xFF2B2625),
    surfaceContainerHighest = Color(0xFF36302E),
    outline = Color(0xFF9C918A),
    outlineVariant = Color(0xFF3F3835),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Spacing, radius and size tokens — use these instead of ad-hoc numbers. */
object Dimens {
    val gutter = 16.dp
    val gap = 12.dp
    val gapS = 8.dp
    val radiusS = 8.dp
    val radiusM = 12.dp
    val radiusL = 16.dp
    /** Minimum touch target (Material / WCAG). */
    val touch = 48.dp
    val buttonTall = 52.dp
}

// Roboto (system sans-serif); headings semibold, prices bold (PRD §8)
private val base = Typography()
val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
)

val PriceStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)

@Composable
fun SwadSangamTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
