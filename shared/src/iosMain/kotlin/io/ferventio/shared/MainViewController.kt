package io.ferventio.shared

import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.ProvideFerventioRuntimeState
import io.ferventio.shared.ui.app.FerventioAuthenticationRoot
import io.ferventio.shared.ui.theme.FerventioTheme
import platform.UIKit.UIViewController

private val iosRuntimeState = FerventioRuntimeState()

fun IosRuntimeState(): FerventioRuntimeState = iosRuntimeState

fun MainViewController(
    onAuthenticate: () -> Unit = {},
    onSignOut: () -> Unit = {},
): UIViewController = ComposeUIViewController {
    ProvideFerventioRuntimeState(iosRuntimeState) {
        FerventioTheme {
            FerventioAuthenticationRoot(
                state = iosRuntimeState.authentication.state,
                workspace = iosRuntimeState.workspace,
                onAuthenticate = onAuthenticate,
                onSignOut = onSignOut,
            )
        }
    }
}
