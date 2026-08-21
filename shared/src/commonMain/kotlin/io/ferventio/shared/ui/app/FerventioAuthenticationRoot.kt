package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.auth.MobileAuthenticationState
import io.ferventio.shared.auth.MobileAuthenticationStatus
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.auth_sign_in_with_twitch
import io.ferventio.shared.push.PushAuthorizationStatus
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.ui.moderation.FerventioModeratedChatScreen
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import org.jetbrains.compose.resources.stringResource

@Composable
fun FerventioAuthenticationRoot(
    state: MobileAuthenticationState,
    workspace: WorkspaceRuntimeStateHolder,
    onAuthenticate: () -> Unit,
    onSignOut: () -> Unit = {},
    pushAuthorizationStatus: PushAuthorizationStatus = PushAuthorizationStatus.UNKNOWN,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSaveSettings: (SharedAppPreferences) -> Unit = {},
    onUpsertHighlightRule: (HighlightRule) -> Unit = {},
    onDeleteHighlightRule: (String) -> Unit = {},
    onUpsertIgnoreRule: (IgnoreRule) -> Unit = {},
    onDeleteIgnoreRule: (String) -> Unit = {},
    onUpsertSavedFilter: (SavedMessageFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
    onImportSavedFilters: (String) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onSetChannelPinned: (String, Boolean) -> Unit = { _, _ -> },
    onRenameChannel: (String, String?) -> Unit = { _, _ -> },
    onRemoveChannel: (String) -> Unit = {},
    onMoveChannel: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    when (state.status) {
        MobileAuthenticationStatus.RESTORING,
        MobileAuthenticationStatus.AUTHORIZING,
        -> FerventioStartupScreen(modifier = modifier)

        MobileAuthenticationStatus.SIGNED_OUT,
        MobileAuthenticationStatus.FAILED,
        -> FerventioSignedOutScreen(
            onAuthenticate = onAuthenticate,
            modifier = modifier,
        )

        MobileAuthenticationStatus.SIGNED_IN -> FerventioWorkspaceShell(
            state = workspace,
            login = state.authentication?.accessLease?.session?.login,
            onSignOut = onSignOut,
            notificationAuthorizationStatus = pushAuthorizationStatus,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onSaveSettings = onSaveSettings,
            onUpsertHighlightRule = onUpsertHighlightRule,
            onDeleteHighlightRule = onDeleteHighlightRule,
            onUpsertIgnoreRule = onUpsertIgnoreRule,
            onDeleteIgnoreRule = onDeleteIgnoreRule,
            onUpsertSavedFilter = onUpsertSavedFilter,
            onDeleteSavedFilter = onDeleteSavedFilter,
            onImportSavedFilters = onImportSavedFilters,
            onSelectChannel = onSelectChannel,
            onAddChannel = onAddChannel,
            onSetChannelPinned = onSetChannelPinned,
            onRenameChannel = onRenameChannel,
            onRemoveChannel = onRemoveChannel,
            onMoveChannel = onMoveChannel,
            modifier = modifier,
        ) { channel ->
            key(channel.id) {
                FerventioModeratedChatScreen(
                    channel = channel,
                    moderatorChannelIds = workspace.moderatorChannelIds,
                )
            }
        }
    }
}

@Composable
private fun FerventioSignedOutScreen(
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FerventioBrandMark()
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAuthenticate) {
                Text(stringResource(Res.string.auth_sign_in_with_twitch))
            }
        }
    }
}
