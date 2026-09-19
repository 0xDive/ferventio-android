package io.ferventio.shared.ui.app

private var iosPushSettingsActions = FerventioPushSettingsActions()

internal actual fun currentPlatformPushSettingsActions(): FerventioPushSettingsActions =
    iosPushSettingsActions

@Suppress("FunctionName")
fun ConfigureIosPushSettingsActions(
    onReconnect: () -> Unit,
    onSelfTest: () -> Unit,
) {
    iosPushSettingsActions = FerventioPushSettingsActions(
        onReconnect = onReconnect,
        onSelfTest = onSelfTest,
    )
}
