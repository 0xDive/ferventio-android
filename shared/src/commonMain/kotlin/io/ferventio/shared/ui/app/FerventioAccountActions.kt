package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class FerventioAccountActions(
    val onReauthorize: (() -> Unit)? = null,
    val onSignOut: () -> Unit = {},
    val onRevokeDevice: (() -> Unit)? = null,
    val onRevokeAllSessions: (() -> Unit)? = null,
) {
    val accountManagementAvailable: Boolean
        get() = onReauthorize != null && onRevokeDevice != null && onRevokeAllSessions != null
}

internal val LocalFerventioAccountActions = staticCompositionLocalOf { FerventioAccountActions() }

@Composable
internal fun ProvideFerventioAccountActions(
    actions: FerventioAccountActions,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFerventioAccountActions provides actions, content = content)
}
