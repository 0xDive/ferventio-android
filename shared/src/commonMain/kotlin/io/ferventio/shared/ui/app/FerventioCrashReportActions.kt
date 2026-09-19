package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class FerventioCrashReportActions(
    val onExport: (() -> Unit)? = null,
    val onClear: (() -> Unit)? = null,
) {
    val localCrashReportsAvailable: Boolean
        get() = onExport != null && onClear != null
}

internal val LocalFerventioCrashReportActions = staticCompositionLocalOf {
    FerventioCrashReportActions()
}

@Composable
internal fun ProvideFerventioCrashReportActions(
    actions: FerventioCrashReportActions,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFerventioCrashReportActions provides actions, content = content)
}
