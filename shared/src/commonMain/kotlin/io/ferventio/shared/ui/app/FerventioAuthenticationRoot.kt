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
import io.ferventio.shared.auth.MobileAuthenticationState
import io.ferventio.shared.auth.MobileAuthenticationStatus
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.auth_sign_in_with_twitch
import io.ferventio.shared.ui.chat.FerventioChatTimeline
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import org.jetbrains.compose.resources.stringResource

@Composable
fun FerventioAuthenticationRoot(
    state: MobileAuthenticationState,
    workspace: WorkspaceRuntimeStateHolder,
    onAuthenticate: () -> Unit,
    onSignOut: () -> Unit = {},
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
            modifier = modifier,
        ) { channel ->
            key(channel.id) {
                FerventioChatTimeline(channel = channel)
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
