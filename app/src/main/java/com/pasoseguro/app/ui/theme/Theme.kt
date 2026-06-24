package com.pasoseguro.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Standard light colour scheme ──────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary            = Brand800,
    onPrimary          = TextOnDark,
    primaryContainer   = Brand50,
    onPrimaryContainer = Brand900,
    secondary          = Brand600,
    onSecondary        = TextOnDark,
    background         = Surface,
    onBackground       = TextPrimary,
    surface            = SurfaceCard,
    onSurface          = TextPrimary,
    surfaceVariant     = Brand50,
    onSurfaceVariant   = TextSecondary,
    error              = AlertRed,
    onError            = TextOnDark,
)

// ── High-contrast light colour scheme ─────────────────────────────────────
// Pure black text on pure white — maximises WCAG contrast ratios.
private val HighContrastColors = lightColorScheme(
    primary            = Brand900,
    onPrimary          = White,
    primaryContainer   = Brand200,
    onPrimaryContainer = Color(0xFF000000),
    secondary          = Brand800,
    onSecondary        = White,
    background         = White,
    onBackground       = Color(0xFF000000),
    surface            = White,
    onSurface          = Color(0xFF000000),
    surfaceVariant     = Color(0xFFE0E0E0),
    onSurfaceVariant   = Color(0xFF212121),
    error              = Color(0xFF8B0000),
    onError            = White,
)

// ── Base accessible typography (sized for low-vision) ─────────────────────
private val AccessibleTypography = Typography(
    displayMedium  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 36.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 26.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 17.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 15.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 13.sp),
)

// ── Large typography (≈25 % scale-up) ─────────────────────────────────────
private val LargeTypography = Typography(
    displayMedium  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 44.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 32.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 21.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 21.sp, lineHeight = 32.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 18.sp, lineHeight = 28.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 18.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp),
)

@Composable
fun PasoSeguroTheme(
    highContrast: Boolean = false,
    largeFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (highContrast) HighContrastColors else LightColors,
        typography  = if (largeFont) LargeTypography else AccessibleTypography,
        content     = content,
    )
}
