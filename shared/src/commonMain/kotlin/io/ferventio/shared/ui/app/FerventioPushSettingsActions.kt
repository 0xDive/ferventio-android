package io.ferventio.shared.ui.app

data class FerventioPushSettingsActions(
    val onReconnect: (() -> Unit)? = null,
    val onSelfTest: (() -> Unit)? = null,
)

internal expect fun currentPlatformPushSettingsActions(): FerventioPushSettingsActions
