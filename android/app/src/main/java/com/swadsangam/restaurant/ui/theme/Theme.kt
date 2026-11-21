package com.swadsangam.restaurant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.swadsangam.restaurant.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design language (FoodGenz-style, licensed by the owner): vivid orange on warm cream, near-black partner surfaces,
// Plus Jakarta Sans. Buttons use the deeper orange so white text passes WCAG AA (4.6:1); the vivid one is the accent.
val BrandOrange = Color(0xFFD63B06)
val BrandOrangeBright = Color(0xFFFD4E11)
val Navy = Color(0xFF131217)
val Saffron = Color(0xFFFFB020)
val Cream = Color(0xFFFFF8F4)
val TextInk = Color(0xFF1B1A1F)
val SuccessGreen = Color(0xFF1F8A4C)
val ErrorRed = Color(0xFFC8392F)
val NonVegBrown = Color(0xFF8B3A1A)

private val Light = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D3),
    onPrimaryContainer = Color(0xFF4A1300),
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0E8),
    onSecondaryContainer = Color(0xFF4A1300),
    tertiary = Saffron,
    onTertiary = TextInk,
    background = Cream,
    onBackground = TextInk,
    surface = Color.White,
    onSurface = TextInk,
    surfaceVariant = Color(0xFFFBF1EA),
    onSurfaceVariant = Color(0xFF6F6A72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFFFF4EE),
    surfaceContainerHigh = Color(0xFFFBEDE5),
    surfaceContainerHighest = Color(0xFFF5E6DD),
    outline = Color(0xFFD9CCC4),
    outlineVariant = Color(0xFFF0E6DF),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5C1210),
)

// Every role is set so no Material default (purple) leaks into dark mode.
private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF131217),
    primaryContainer = Color(0xFF5A2410),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFC9C5CC),
    onSecondary = Color(0xFF131217),
    secondaryContainer = Color(0xFF2A1A14),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Saffron,
    onTertiary = Color(0xFF2B1C00),
    background = Color(0xFF131217),
    onBackground = Color(0xFFF4F1F3),
    surface = Color(0xFF1C1B21),
    onSurface = Color(0xFFF4F1F3),
    surfaceVariant = Color(0xFF25232B),
    onSurfaceVariant = Color(0xFFA7A2AB),
    surfaceContainerLowest = Color(0xFF0D0C10),
    surfaceContainerLow = Color(0xFF18171C),
    surfaceContainer = Color(0xFF1C1B21),
    surfaceContainerHigh = Color(0xFF25232B),
    surfaceContainerHighest = Color(0xFF2E2C34),
    outline = Color(0xFF8A858F),
    outlineVariant = Color(0xFF2E2C34),
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
    val radiusM = 14.dp
    val radiusL = 20.dp
    /** Minimum touch target (Material / WCAG). */
    val touch = 48.dp
    val buttonTall = 52.dp
}

// Plus Jakarta Sans (bundled, SIL OFL) — headings bold/extra-bold, prices bold
val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)
private val base = Typography()
val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontFamily = JakartaSans, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = JakartaSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = JakartaSans),
    bodySmall = base.bodySmall.copy(fontFamily = JakartaSans),
    labelLarge = base.labelLarge.copy(fontFamily = JakartaSans, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
)

/** Softer, rounder shapes (cards 18 dp, sheets 24 dp) */
val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

val PriceStyle = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)

@Composable
fun SwadSangamTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
