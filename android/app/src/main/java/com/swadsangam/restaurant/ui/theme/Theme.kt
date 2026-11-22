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
private val BrandOrange = Color(0xFFD03905) // #D63B06 nudged darker: 4.7:1 on cream as well as white
private val BrandOrangeBright = Color(0xFFFD4E11)
private val Navy = Color(0xFF131217)
private val Saffron = Color(0xFFFFB020)
private val Cream = Color(0xFFFFF8F4)
private val TextInk = Color(0xFF1B1A1F)
private val ErrorRed = Color(0xFFC8392F)

private val Light = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D3),
    onPrimaryContainer = Color(0xFF4A1300),
    secondary = Color(0xFF1F5F2C),   // logo green
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
    outline = Color(0xFF8F837B),        // 3.5:1 — checkbox, radio, field borders must be visible (WCAG 1.4.11)
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
    secondary = Color(0xFF8FD19E),   // logo green, lightened for dark mode
    onSecondary = Color(0xFF0B2416),
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

val PriceStyle = TextStyle(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)

@Composable
fun SwadSangamTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
