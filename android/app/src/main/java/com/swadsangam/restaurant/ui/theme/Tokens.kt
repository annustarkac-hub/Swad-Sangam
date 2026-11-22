package com.swadsangam.restaurant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Swad Sangam design tokens — the single source for spacing, shape, size, elevation and semantic colour.
 * Screens must use these (or MaterialTheme roles), never raw numbers or hex values.
 * Rhythm follows a 4/8 dp grid (UI UX Pro Max: "8dp spacing rhythm", "touch target ≥ 48dp").
 */

/** Spacing scale (4 dp grid). Gutter = horizontal screen padding; section = gap between page sections. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val gutter = l
    val section = xl
}

/** Corner radius scale. Shapes in [AppShapes] map to these. */
object Radius {
    val xxs = 3.dp     // veg/non-veg mark square
    val xs = 6.dp      // tiny badges
    val s = 10.dp      // chips, steppers, small images
    val m = 14.dp      // buttons, text fields, list images
    val l = 20.dp      // cards
    val xl = 28.dp     // bottom sheets, dialogs, hero cards
}

/** Icon sizes. Icons inside buttons use [m]; standalone list icons [l]; empty-state illustrations [hero]. */
object IconSize {
    val xs = 14.dp
    val s = 18.dp
    val m = 20.dp
    val l = 24.dp
    val hero = 64.dp
}

/** Component sizes. Every tappable thing is at least [touch]. */
object Size {
    val touch = 48.dp
    val buttonLarge = 52.dp
    val buttonMedium = 44.dp
    val thumbnail = 104.dp
    val avatar = 52.dp
    val cardWidth = 168.dp
    val cardImage = 112.dp
    val heroBanner = 180.dp
    val heroImage = 240.dp
    val offerCard = 280.dp
    val offerImage = 100.dp
    val stepIcon = 24.dp
    val iconContainer = 40.dp   // circular icon badge in list rows
    val listRow = 56.dp         // min height of a one/two-line list row
    val chip = 40.dp            // min height of a dismissible banner row
    val vegMark = 16.dp
}

/** Stroke widths. */
object Border {
    val hairline = 1.dp
    val mark = 1.5.dp
    val progress = 2.dp
}

/** Elevation scale (tonal elevation in Material 3). */
object Elevation {
    val none = 0.dp
    val low = 1.dp
    val mid = 3.dp
    val high = 6.dp
    val barShadow = 8.dp        // shadow under the sticky bottom action bar
}

/** Semantic colours Material 3 has no role for. Read through [AppTheme.colors]. */
@Immutable
data class AppColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val veg: Color,
    val nonVeg: Color,
    val rating: Color,
    val brandAccent: Color,
    val brandGreen: Color,
    val onBrandHeader: Color,
)

internal val LightAppColors = AppColors(
    success = Color(0xFF1F8A4C), onSuccess = Color.White, successContainer = Color(0xFFE5F5EB), onSuccessContainer = Color(0xFF0B3D1E),
    warning = Color(0xFFB45309), warningContainer = Color(0xFFFFF3DC), onWarningContainer = Color(0xFF4A2A00),
    info = Color(0xFF1F5F2C), infoContainer = Color(0xFFFFF0E8), onInfoContainer = Color(0xFF4A1300),
    veg = Color(0xFF1F8A4C), nonVeg = Color(0xFF8B3A1A), rating = Color(0xFFC77C00),
    brandAccent = Color(0xFFFD4E11), brandGreen = Color(0xFF1F5F2C), onBrandHeader = Color.White,
)

internal val DarkAppColors = AppColors(
    success = Color(0xFF5BD08A), onSuccess = Color(0xFF0B2416), successContainer = Color(0xFF16301F), onSuccessContainer = Color(0xFFC9F0D6),
    warning = Color(0xFFFFB454), warningContainer = Color(0xFF3A2D14), onWarningContainer = Color(0xFFFFE2B8),
    info = Color(0xFF8FD19E), infoContainer = Color(0xFF2A1A14), onInfoContainer = Color(0xFFFFDBC9),
    veg = Color(0xFF5BD08A), nonVeg = Color(0xFFE08A64), rating = Color(0xFFFFC24D),
    brandAccent = Color(0xFFFF7A45), brandGreen = Color(0xFF8FD19E), onBrandHeader = Color.White,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access point for the app's extra tokens: `AppTheme.colors.success`, etc. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/** Convenience: muted text colour used for secondary lines. */
val secondaryText: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
