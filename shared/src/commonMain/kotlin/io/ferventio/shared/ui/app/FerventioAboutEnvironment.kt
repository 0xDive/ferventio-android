package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalFerventioAboutInfo = staticCompositionLocalOf { FerventioAboutInfo() }

@Composable
internal fun ProvideFerventioAboutInfo(
    info: FerventioAboutInfo,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFerventioAboutInfo provides info, content = content)
}
