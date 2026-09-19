package io.ferventio.shared.ui.app

data class FerventioDiagnosticsActions(
    val onReconnect: (() -> Unit)? = null,
) {
    val reconnectAvailable: Boolean
        get() = onReconnect != null
}

internal expect fun currentFerventioDiagnosticsActions(): FerventioDiagnosticsActions
