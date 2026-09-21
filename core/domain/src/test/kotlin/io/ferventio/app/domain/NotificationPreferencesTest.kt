package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesTest {
    @Test
    fun channelOverridesInheritGlobalEventsUntilExplicitlyChanged() {
        val preferences = NotificationPreferences()
            .withGlobalEvent(NotificationEventType.REPLY.ruleId, false)
            .enableChannelOverrides("channel")
            .withChannelEvent("channel", NotificationEventType.REPLY.ruleId, true)

        assertFalse(preferences.isEnabled("reply", "other"))
        assertTrue(preferences.isEnabled("reply", "channel"))
        assertTrue(preferences.isEnabled("mention", "channel"))
    }

    @Test
    fun clearingSingleChannelEventOverrideRestoresGlobalInheritance() {
        val preferences = NotificationPreferences()
            .withGlobalEvent("reply", false)
            .withChannelEvent("channel", "reply", true)
            .withChannelEvent("channel", "mention", false)

        val restored = preferences.clearChannelEventOverride("channel", "reply")

        assertFalse(restored.isEnabled("reply", "channel"))
        assertFalse(restored.isEnabled("mention", "channel"))
        assertTrue("reply" !in restored.channelOverrides.getValue("channel").eventOverrides)
        assertTrue("mention" in restored.channelOverrides.getValue("channel").eventOverrides)
    }

    @Test
    fun clearingAllChannelEventOverridesKeepsChannelMasterAndRestoresInheritance() {
        val preferences = NotificationPreferences()
            .withGlobalEvent("reply", false)
            .withChannelEnabled("channel", false)
            .withChannelEvent("channel", "reply", true)
            .withChannelEvent("channel", "mention", false)

        val restored = preferences.clearChannelEventOverrides("channel")

        assertFalse(restored.channelOverrides.getValue("channel").enabled)
        assertTrue(restored.channelOverrides.getValue("channel").eventOverrides.isEmpty())
        assertFalse(restored.isEnabled("reply", "channel"))
        assertFalse(restored.isEnabled("mention", "channel"))
    }

    @Test
    fun disabledChannelSuppressesEveryEventWithoutExpandingOverrides() {
        val preferences = NotificationPreferences()
            .withChannelEnabled("channel", false)

        assertFalse(preferences.isEnabled("reply", "channel"))
        assertFalse(preferences.isEnabled("automod_hold", "channel"))
        assertTrue(preferences.isEnabled("reply", "other"))
        assertTrue(preferences.channelOverrides.getValue("channel").eventOverrides.isEmpty())
    }

    @Test
    fun backendRuleProjectionKeepsEventWhenAnyChannelNeedsIt() {
        val preferences = NotificationPreferences()
            .withGlobalEvent("reply", false)
            .withChannelEvent("one", "reply", true)
            .withGlobalEvent("automod_hold", false)

        val rules = preferences.enabledRuleIds(listOf("one", "two"))

        assertTrue("reply" in rules)
        assertFalse("automod_hold" in rules)
        assertEquals(NotificationEventType.entries.size - 1, rules.size)
    }

    @Test
    fun legacyDefaultsRemainEffectiveWhenNoNewOverrideExists() {
        val legacy: (String) -> Boolean = { rule -> rule != "reply" && rule != "automod_hold" }
        val preferences = NotificationPreferences()

        assertFalse(preferences.isEnabled("reply", "channel", legacy))
        assertFalse(preferences.isEnabled("automod_hold", "channel", legacy))
        assertTrue(preferences.isEnabled("mention", "channel", legacy))
    }

    @Test
    fun normalizationDropsUnknownEventsAndBlankChannels() {
        val preferences = NotificationPreferences(
            eventOverrides = mapOf("reply" to false, "future_event" to false),
            channelOverrides = mapOf(
                " " to ChannelNotificationPreferences(enabled = false),
                " channel " to ChannelNotificationPreferences(
                    eventOverrides = mapOf("automod_hold" to false, "unknown" to false),
                ),
            ),
        ).normalized()

        assertEquals(mapOf("reply" to false), preferences.eventOverrides)
        assertEquals(setOf("channel"), preferences.channelOverrides.keys)
        assertEquals(
            mapOf("automod_hold" to false),
            preferences.channelOverrides.getValue("channel").eventOverrides,
        )
    }
}
