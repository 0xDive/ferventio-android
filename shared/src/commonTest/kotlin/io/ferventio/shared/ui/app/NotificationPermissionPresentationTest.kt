package io.ferventio.shared.ui.app

import io.ferventio.shared.push.PushAuthorizationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionPresentationTest {
    @Test
    fun unknownAndNotDeterminedRequestPermission() {
        assertEquals(
            NotificationPermissionAction.REQUEST_PERMISSION,
            notificationPermissionAction(PushAuthorizationStatus.UNKNOWN),
        )
        assertEquals(
            NotificationPermissionAction.REQUEST_PERMISSION,
            notificationPermissionAction(PushAuthorizationStatus.NOT_DETERMINED),
        )
    }

    @Test
    fun deniedOpensPlatformSettings() {
        assertEquals(
            NotificationPermissionAction.OPEN_SETTINGS,
            notificationPermissionAction(PushAuthorizationStatus.DENIED),
        )
    }

    @Test
    fun grantedStatusesNeedNoAction() {
        listOf(
            PushAuthorizationStatus.AUTHORIZED,
            PushAuthorizationStatus.PROVISIONAL,
            PushAuthorizationStatus.EPHEMERAL,
        ).forEach { status ->
            assertEquals(
                NotificationPermissionAction.NONE,
                notificationPermissionAction(status),
            )
        }
    }
}
