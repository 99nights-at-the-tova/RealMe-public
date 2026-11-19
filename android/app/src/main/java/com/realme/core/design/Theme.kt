package com.realme.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val Mint = Color(0xFF8FD6B0)
val Lavender = Color(0xFFD7B49A)
val DarkBackground = Color(0xFF0E120F)
val DarkSurface = Color(0xFF161B18)
val DarkSurfaceVariant = Color(0xFF2A312C)

private val RealMeColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF062017),
    primaryContainer = Color(0xFF1E3A2C),
    onPrimaryContainer = Color(0xFFC9F2DC),
    secondary = Lavender,
    onSecondary = Color(0xFF2A1A12),
    secondaryContainer = Color(0xFF3E2C22),
    onSecondaryContainer = Color(0xFFF1DCCB),
    tertiary = Color(0xFFAEB6C2),
    onTertiary = Color(0xFF1A2028),
    tertiaryContainer = Color(0xFF2C333D),
    onTertiaryContainer = Color(0xFFDDE3EA),
    background = DarkBackground,
    onBackground = Color(0xFFE3E8E3),
    surface = DarkSurface,
    onSurface = Color(0xFFE3E8E3),
    surfaceContainerLowest = Color(0xFF090C0A),
    surfaceContainerLow = Color(0xFF121714),
    surfaceContainer = Color(0xFF1A201C),
    surfaceContainerHigh = Color(0xFF212823),
    surfaceContainerHighest = Color(0xFF29312B),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9B2AA),
    outline = Color(0xFF6E7A71),
    outlineVariant = Color(0xFF333B35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF3B0904),
)

private val RealMeTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val RealMeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun RealMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RealMeColors,
        typography = RealMeTypography,
        shapes = RealMeShapes,
    ) {
        Surface(
            color = RealMeColors.background,
            contentColor = RealMeColors.onBackground,
            content = content,
        )
    }
}
