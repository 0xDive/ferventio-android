package io.ferventio.shared

import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.shared.history.IosChatHistoryStore
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.ProvideFerventioRuntimeState
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.ui.app.FerventioAuthenticationRoot
import io.ferventio.shared.ui.theme.FerventioTheme
import io.ferventio.shared.ui.theme.FerventioThemeMode
import platform.UIKit.UIViewController

private val iosRuntimeState = FerventioRuntimeState(history = IosChatHistoryStore())

fun IosRuntimeState(): FerventioRuntimeState = iosRuntimeState

fun MainViewController(
    onAuthenticate: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSaveSettings: (SharedAppPreferences) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onSetChannelPinned: (String, Boolean) -> Unit = { _, _ -> },
    onRenameChannel: (String, String?) -> Unit = { _, _ -> },
    onRemoveChannel: (String) -> Unit = {},
    onMoveChannel: (String, Int) -> Unit = { _, _ -> },
): UIViewController = ComposeUIViewController {
    val preferences = iosRuntimeState.settings.preferences
    ProvideFerventioRuntimeState(iosRuntimeState) {
        FerventioTheme(
            themeMode = when (preferences.themeMode) {
                AppThemeMode.LIGHT -> FerventioThemeMode.LIGHT
                AppThemeMode.DARK -> FerventioThemeMode.DARK
                AppThemeMode.AMOLED -> FerventioThemeMode.AMOLED
            },
            fontScalePercent = preferences.fontScalePercent,
        ) {
            FerventioAuthenticationRoot(
                state = iosRuntimeState.authentication.state,
                workspace = iosRuntimeState.workspace,
                onAuthenticate = onAuthenticate,
                onSignOut = onSignOut,
                pushAuthorizationStatus = iosRuntimeState.pushRegistration.authorizationStatus,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onSaveSettings = onSaveSettings,
                onSelectChannel = onSelectChannel,
                onAddChannel = onAddChannel,
                onSetChannelPinned = onSetChannelPinned,
                onRenameChannel = onRenameChannel,
                onRemoveChannel = onRemoveChannel,
                onMoveChannel = onMoveChannel,
            )
        }
    }
}
