package io.ferventio.shared.ui.app

import io.ferventio.shared.IosRuntimeState

private var iosAuthenticatedChatReconnectAction: (() -> Unit)? = null

fun SetIosAuthenticatedChatReconnectAction(action: (() -> Unit)?) {
    iosAuthenticatedChatReconnectAction = action
}

internal actual fun currentFerventioDiagnosticsActions(): FerventioDiagnosticsActions =
    FerventioDiagnosticsActions(
        onReconnect = if (IosRuntimeState().authentication.state.authentication != null) {
            iosAuthenticatedChatReconnectAction
        } else {
            null
        },
    )
