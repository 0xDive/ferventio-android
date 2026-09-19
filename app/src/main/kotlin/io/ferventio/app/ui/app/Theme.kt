package io.ferventio.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.shared.ui.theme.FerventioTheme as SharedFerventioTheme
import io.ferventio.shared.ui.theme.FerventioThemeMode

@Composable
fun FerventioTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    fontScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
            isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
        }
    }

    SharedFerventioTheme(
        themeMode = themeMode.toSharedThemeMode(),
        fontScalePercent = fontScalePercent,
        content = content,
    )
}

private fun AppThemeMode.toSharedThemeMode(): FerventioThemeMode = when (this) {
    AppThemeMode.LIGHT -> FerventioThemeMode.LIGHT
    AppThemeMode.DARK -> FerventioThemeMode.DARK
    AppThemeMode.AMOLED -> FerventioThemeMode.AMOLED
}
