package io.ferventio.shared.push

import io.ferventio.app.domain.NotificationPreferences
import io.ferventio.shared.settings.SharedAppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PushNotificationPolicyTest {
    private val policy = PushNotificationPolicy()

    @Test
    fun disabledMasterUsesNonEmptyBackendSentinel() {
        val preferences = SharedAppPreferences(
            notificationPreferences = NotificationPreferences(enabled = false),
        )

        assertEquals(
            listOf(PushNotificationPolicy.BACKEND_DISABLED_RULE),
            policy.enabledRules(preferences, listOf("channel")),
        )
    }

    @Test
    fun sentinelIsNotARealNotificationEvent() {
        assertFalse(
            io.ferventio.app.domain.NotificationEventType.allRuleIds
                .contains(PushNotificationPolicy.BACKEND_DISABLED_RULE),
        )
    }
}
