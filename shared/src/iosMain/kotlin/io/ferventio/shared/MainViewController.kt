package io.ferventio.shared

import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.ProvideFerventioRuntimeState
import io.ferventio.shared.ui.app.FerventioStartupScreen
import io.ferventio.shared.ui.theme.FerventioTheme
import platform.UIKit.UIViewController

private val iosRuntimeState = FerventioRuntimeState()

fun IosRuntimeState(): FerventioRuntimeState = iosRuntimeState

fun MainViewController(): UIViewController = ComposeUIViewController {
    ProvideFerventioRuntimeState(iosRuntimeState) {
        FerventioTheme {
            FerventioStartupScreen()
        }
    }
}
