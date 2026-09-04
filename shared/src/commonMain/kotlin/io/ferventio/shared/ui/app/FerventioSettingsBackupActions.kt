package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class FerventioSettingsBackupActions(
    val onExport: (() -> Unit)? = null,
    val onImport: (() -> Unit)? = null,
    val onKeepLocal: (() -> Unit)? = null,
    val onUseServer: (() -> Unit)? = null,
) {
    val fileTransferAvailable: Boolean
        get() = onExport != null || onImport != null

    val conflictResolutionAvailable: Boolean
        get() = onKeepLocal != null && onUseServer != null
}

internal val LocalFerventioSettingsBackupActions = staticCompositionLocalOf {
    FerventioSettingsBackupActions()
}

@Composable
internal fun ProvideFerventioSettingsBackupActions(
    actions: FerventioSettingsBackupActions,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFerventioSettingsBackupActions provides actions,
        content = content,
    )
}
