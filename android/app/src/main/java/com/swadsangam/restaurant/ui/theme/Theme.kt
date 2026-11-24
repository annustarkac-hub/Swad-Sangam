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

// Swad Sangam's own palette (same tokens as the website and the admin app): tandoor crimson + saffron on warm
// porcelain, logo green as secondary. Replaces the earlier orange, which customers said looked like another food app.
private val Crimson = Color(0xFFB83227)        // 5.9:1 with white, 5.5:1 on porcelain
private val Saffron = Color(0xFFC27C0E)        // 3.4:1 on white (icons/stars)
private val Porcelain = Color(0xFFF8F6F3)
private val TextInk = Color(0xFF1A1716)
private val ErrorRed = Color(0xFFC8332B)

private val Light = lightColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDF0EE),
    onPrimaryContainer = Color(0xFF5C1610),
    secondary = Color(0xFF1F5F2C),   // logo green
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3E0),
    onSecondaryContainer = Color(0xFF4A2A00),
    tertiary = Saffron,
    onTertiary = Color.White,
    background = Porcelain,
    onBackground = TextInk,
    surface = Color.White,
    onSurface = TextInk,
    surfaceVariant = Color(0xFFF2EFEA),
    onSurfaceVariant = Color(0xFF6B635E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFBF9),
    surfaceContainer = Color(0xFFF6F3EF),
    surfaceContainerHigh = Color(0xFFF2EFEA),
    surfaceContainerHighest = Color(0xFFEAE5DF),
    outline = Color(0xFF8F837B),        // 3.5:1 — checkbox, radio, field borders must be visible (WCAG 1.4.11)
    outlineVariant = Color(0xFFEAE5DF),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5C1210),
    inverseSurface = Color(0xFF2A2527),
    inverseOnSurface = Color(0xFFF4F1F0),
    inversePrimary = Color(0xFFFF7A6B),
)

// Every role is set so no Material default (purple) leaks into dark mode.
private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A6B),
    onPrimary = Color(0xFF141214),
    primaryContainer = Color(0xFF4A2421),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFF8FD19E),   // logo green, lightened for dark mode
    onSecondary = Color(0xFF0B2416),
    secondaryContainer = Color(0xFF3A2B12),
    onSecondaryContainer = Color(0xFFFFE2B8),
    tertiary = Color(0xFFF5B742),
    onTertiary = Color(0xFF2B1C00),
    background = Color(0xFF121115),
    onBackground = Color(0xFFF4F1F0),
    surface = Color(0xFF1C1B21),
    onSurface = Color(0xFFF4F1F0),
    surfaceVariant = Color(0xFF26242D),
    onSurfaceVariant = Color(0xFFA59FA6),
    surfaceContainerLowest = Color(0xFF0D0C10),
    surfaceContainerLow = Color(0xFF18171C),
    surfaceContainer = Color(0xFF1C1B21),
    surfaceContainerHigh = Color(0xFF26242D),
    surfaceContainerHighest = Color(0xFF2F2D36),
    outline = Color(0xFF8A858F),
    outlineVariant = Color(0xFF2F2D36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4F1F0),
    inverseOnSurface = Color(0xFF1C1B21),
    inversePrimary = Crimson,
)

// Plus Jakarta Sans (bundled, SIL OFL) — headings bold/extra-bold, prices bold
val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)
private val base = Typography()
/** Complete type scale on Plus Jakarta Sans. Display/headline for page titles, title for cards and sections,
 *  body for content (16 sp base), label for buttons, chips and captions. */
private fun TextStyle.jakarta(weight: FontWeight, spacing: Float? = null) =
    copy(fontFamily = JakartaSans, fontWeight = weight, letterSpacing = spacing?.sp ?: letterSpacing)
val AppTypography = Typography(
    displayLarge = base.displayLarge.jakarta(FontWeight.ExtraBold, -0.5f),
    displayMedium = base.displayMedium.jakarta(FontWeight.ExtraBold, -0.4f),
    displaySmall = base.displaySmall.jakarta(FontWeight.ExtraBold, -0.3f),
    headlineLarge = base.headlineLarge.jakarta(FontWeight.ExtraBold, -0.3f),
    headlineMedium = base.headlineMedium.jakarta(FontWeight.ExtraBold, -0.3f),
    headlineSmall = base.headlineSmall.jakarta(FontWeight.Bold, -0.2f),
    titleLarge = base.titleLarge.jakarta(FontWeight.Bold, -0.2f),
    titleMedium = base.titleMedium.jakarta(FontWeight.Bold, 0f),
    titleSmall = base.titleSmall.jakarta(FontWeight.SemiBold, 0f),
    bodyLarge = base.bodyLarge.jakarta(FontWeight.Normal, 0f),
    bodyMedium = base.bodyMedium.jakarta(FontWeight.Normal, 0f),
    bodySmall = base.bodySmall.jakarta(FontWeight.Normal, 0f),
    labelLarge = base.labelLarge.jakarta(FontWeight.Bold, 0f),
    labelMedium = base.labelMedium.jakarta(FontWeight.SemiBold, 0f),
    labelSmall = base.labelSmall.jakarta(FontWeight.SemiBold, 0.2f),
)

/** Shapes: chips 10, cards 20, sheets/dialogs 28 (see [Radius]). */
val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Radius.s),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Radius.l),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xl),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xl),
)

/** Prices and quantities use tabular figures so numbers never jump when they change. */
val PriceStyle = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFeatureSettings = "tnum")

@Composable
fun SwadSangamTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
