package io.ferventio.shared.ui.app

import io.ferventio.shared.push.PushAuthorizationStatus

internal enum class NotificationPermissionAction {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
    NONE,
}

internal fun notificationPermissionAction(
    status: PushAuthorizationStatus,
): NotificationPermissionAction = when (status) {
    PushAuthorizationStatus.UNKNOWN,
    PushAuthorizationStatus.NOT_DETERMINED,
    -> NotificationPermissionAction.REQUEST_PERMISSION

    PushAuthorizationStatus.DENIED -> NotificationPermissionAction.OPEN_SETTINGS

    PushAuthorizationStatus.AUTHORIZED,
    PushAuthorizationStatus.PROVISIONAL,
    PushAuthorizationStatus.EPHEMERAL,
    -> NotificationPermissionAction.NONE
}
