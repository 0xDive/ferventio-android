package io.ferventio.app.ui

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.ferventio.app.domain.AppThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF2B1B00),
    primaryContainer = Color(0xFF4A3510),
    onPrimaryContainer = Color(0xFFFFE3AC),
    secondary = Color(0xFFC7A8FF),
    onSecondary = Color(0xFF321661),
    secondaryContainer = Color(0xFF40266E),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF72D9A2),
    onTertiary = Color(0xFF003921),
    tertiaryContainer = Color(0xFF145334),
    onTertiaryContainer = Color(0xFF9DF8C0),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFECE7EF),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFECE7EF),
    surfaceVariant = Color(0xFF1C1C24),
    onSurfaceVariant = Color(0xFFC9C3CE),
    outline = Color(0xFF3A3943),
    outlineVariant = Color(0xFF282730),
    error = Color(0xFFFFB4AB),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF171717),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFF151515),
    outline = Color(0xFF373737),
    outlineVariant = Color(0xFF242424),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF229ED9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9F1FC),
    onPrimaryContainer = Color(0xFF06364B),
    secondary = Color(0xFF229ED9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9F1FC),
    onSecondaryContainer = Color(0xFF06364B),
    tertiary = Color(0xFF2E9B66),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8F4E5),
    onTertiaryContainer = Color(0xFF0D3D29),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFF2F2F7),
    surfaceContainerHighest = Color(0xFFE9E9EE),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF7C7C80),
    outline = Color(0xFFB8B8BD),
    outlineVariant = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFF75CCF1),
    surfaceTint = Color(0xFF229ED9),
    scrim = Color(0xFF000000),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE2E0),
    onErrorContainer = Color(0xFF5F1110),
)

private val FerventioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BaseTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FerventioTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    fontScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val scale = fontScalePercent.coerceIn(80, 150) / 100f
    val typography = remember(scale) { BaseTypography.scaled(scale) }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
            isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
        }
    }
    MaterialTheme(
        colorScheme = when (themeMode) {
            AppThemeMode.LIGHT -> LightColors
            AppThemeMode.DARK -> DarkColors
            AppThemeMode.AMOLED -> AmoledColors
        },
        typography = typography,
        shapes = FerventioShapes,
        content = content,
    )
}

private fun Typography.scaled(scale: Float): Typography = copy(
    displayLarge = displayLarge.scaled(scale),
    displayMedium = displayMedium.scaled(scale),
    displaySmall = displaySmall.scaled(scale),
    headlineLarge = headlineLarge.scaled(scale),
    headlineMedium = headlineMedium.scaled(scale),
    headlineSmall = headlineSmall.scaled(scale),
    titleLarge = titleLarge.scaled(scale),
    titleMedium = titleMedium.scaled(scale),
    titleSmall = titleSmall.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale),
    bodyMedium = bodyMedium.scaled(scale),
    bodySmall = bodySmall.scaled(scale),
    labelLarge = labelLarge.scaled(scale),
    labelMedium = labelMedium.scaled(scale),
    labelSmall = labelSmall.scaled(scale),
)

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaled(scale),
    lineHeight = lineHeight.scaled(scale),
    letterSpacing = letterSpacing.scaled(scale),
)

private fun TextUnit.scaled(scale: Float): TextUnit = if (this != TextUnit.Unspecified) value.times(scale).sp else this
