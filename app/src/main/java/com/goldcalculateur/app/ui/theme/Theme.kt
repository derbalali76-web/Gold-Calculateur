package com.goldcalculateur.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette: deep navy + elegant gold. Success green, alert red. */
object Palette {
    val Navy = Color(0xFF000F20)
    val NavyCard = Color(0xFF011422)
    val NavyCardAlt = Color(0xFF0B2A44)
    val NavyOutline = Color(0xFF234D67)
    val Gold = Color(0xFFD4AF37)
    val GoldBright = Color(0xFFF2CC5A)
    val GoldDeep = Color(0xFF8A6A00)
    val Cream = Color(0xFFF5F1E6)
    val CreamDim = Color(0xFFB7C0D6)
    val SuccessDark = Color(0xFF4FE08C)
    val SuccessLight = Color(0xFF1B7F4B)
    val WarningDark = Color(0xFFFFC857)
    val WarningLight = Color(0xFF9A6200)
    val ErrorDark = Color(0xFFFF6B6B)
    val ErrorLight = Color(0xFFC62828)
    val LightBg = Color(0xFFF7F3E8)
    val LightCard = Color(0xFFFFFFFF)
    val LightCardAlt = Color(0xFFEFE8D3)
    val LightOutline = Color(0xFFCDBF93)
    val LightText = Color(0xFF0A1428)
    val LightTextDim = Color(0xFF4A5675)
}

@Immutable
data class ExtraColors(
    val gold: Color,
    val goldText: Color,
    val onGold: Color,
    val success: Color,
    val warning: Color,
    val cardAlt: Color,
    val fieldBackground: Color,
    val isDark: Boolean,
)

private val DarkExtras = ExtraColors(
    gold = Palette.Gold, goldText = Palette.GoldBright, onGold = Palette.Navy,
    success = Palette.SuccessDark, warning = Palette.WarningDark,
    cardAlt = Palette.NavyCardAlt, fieldBackground = Palette.Navy, isDark = true,
)
private val LightExtras = ExtraColors(
    gold = Palette.Gold, goldText = Palette.GoldDeep, onGold = Palette.Navy,
    success = Palette.SuccessLight, warning = Palette.WarningLight,
    cardAlt = Palette.LightCardAlt, fieldBackground = Palette.LightBg, isDark = false,
)

private val LocalExtraColors = staticCompositionLocalOf { DarkExtras }

val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

private val DarkScheme = darkColorScheme(
    primary = Palette.Gold, onPrimary = Palette.Navy,
    secondary = Palette.GoldBright, onSecondary = Palette.Navy,
    background = Palette.Navy, onBackground = Palette.Cream,
    surface = Palette.NavyCard, onSurface = Palette.Cream,
    surfaceVariant = Palette.NavyCardAlt, onSurfaceVariant = Palette.CreamDim,
    outline = Palette.NavyOutline, error = Palette.ErrorDark, onError = Palette.Navy,
)

private val LightScheme = lightColorScheme(
    primary = Palette.Gold, onPrimary = Palette.Navy,
    secondary = Palette.GoldDeep, onSecondary = Color.White,
    background = Palette.LightBg, onBackground = Palette.LightText,
    surface = Palette.LightCard, onSurface = Palette.LightText,
    surfaceVariant = Palette.LightCardAlt, onSurfaceVariant = Palette.LightTextDim,
    outline = Palette.LightOutline, error = Palette.ErrorLight, onError = Color.White,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun GoldTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
