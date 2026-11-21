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
import androidx.compose.ui.unit.sp

// PRD §8 proposed brand palette (not verified existing brand colours)
val BrandRed = Color(0xFF9E3528)
val Saffron = Color(0xFFE8A13B)
val Cream = Color(0xFFFFF8EF)
val TextBrown = Color(0xFF34241F)
val SuccessGreen = Color(0xFF27834A)
val ErrorRed = Color(0xFFC83D35)
val NonVegBrown = Color(0xFF8B3A1A)

private val Light = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3E0500),
    secondary = Saffron,
    onSecondary = TextBrown,
    secondaryContainer = Color(0xFFFFE3BC),
    onSecondaryContainer = TextBrown,
    background = Cream,
    onBackground = TextBrown,
    surface = Color.White,
    onSurface = TextBrown,
    surfaceVariant = Color(0xFFF6EBDD),
    onSurfaceVariant = Color(0xFF6B5A53),
    outline = Color(0xFFD7C2B3),
    error = ErrorRed,
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    onPrimary = Color(0xFF5F1409),
    primaryContainer = Color(0xFF7E2418),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Saffron,
    onSecondary = Color(0xFF2B1C00),
    background = Color(0xFF1E1513),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF2A201D),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF3A2E2A),
    onSurfaceVariant = Color(0xFFD8C2BB),
    error = Color(0xFFFFB4AB),
)

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
