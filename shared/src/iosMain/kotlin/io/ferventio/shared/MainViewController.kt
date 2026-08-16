package io.ferventio.shared

import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.shared.ui.app.FerventioStartupScreen
import io.ferventio.shared.ui.theme.FerventioTheme
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    FerventioTheme {
        FerventioStartupScreen()
    }
}
