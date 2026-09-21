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
    fun channelOverridesMaterializeEffectiveRuleLists() {
        val preferences = SharedAppPreferences(
            notificationPreferences = NotificationPreferences()
                .withGlobalEvent("reply", false)
                .withChannelEvent("one", "reply", true)
                .withChannelEnabled("two", false),
        )

        val rules = policy.channelRuleOverrides(
            preferences = preferences,
            channelIds = listOf("one", "two", "three"),
        )

        assertEquals(true, "reply" in rules.getValue("one"))
        assertEquals(
            listOf(PushNotificationPolicy.BACKEND_DISABLED_RULE),
            rules.getValue("two"),
        )
        assertEquals(false, "three" in rules)
    }

    @Test
    fun temporaryMuteBlocksLocalDeliveryButKeepsBackendRulesConfigured() {
        val preferences = SharedAppPreferences(
            notificationPreferences = NotificationPreferences()
                .withChannelMutedUntil("one", 10_000L),
        )

        assertFalse(
            policy.isEnabled(
                preferences = preferences,
                ruleId = "reply",
                channelId = "one",
                nowEpochMillis = 9_999L,
            ),
        )
        assertEquals(
            true,
            "reply" in policy.channelRuleOverrides(
                preferences = preferences,
                channelIds = listOf("one"),
            ).getValue("one"),
        )
        assertEquals(
            mapOf("one" to 10_000L),
            policy.channelMutedUntilEpochMillis(preferences, listOf("one")),
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
