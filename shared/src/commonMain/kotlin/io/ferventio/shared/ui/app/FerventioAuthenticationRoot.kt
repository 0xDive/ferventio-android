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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.shared.auth.MobileAuthenticationState
import io.ferventio.shared.auth.MobileAuthenticationStatus

@Composable
fun FerventioAuthenticationRoot(
    state: MobileAuthenticationState,
    onAuthenticate: () -> Unit,
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

        MobileAuthenticationStatus.SIGNED_IN -> FerventioSignedInLanding(
            login = state.authentication?.accessLease?.session?.login,
            modifier = modifier,
        )
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
                Text("Twitch")
            }
        }
    }
}

@Composable
private fun FerventioSignedInLanding(
    login: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FerventioBrandMark()
            login?.takeIf(String::isNotBlank)?.let { value ->
                Spacer(Modifier.height(16.dp))
                Text("@$value")
            }
        }
    }
}
