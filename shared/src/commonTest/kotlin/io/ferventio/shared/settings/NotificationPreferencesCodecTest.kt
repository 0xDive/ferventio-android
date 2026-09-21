package io.ferventio.shared.settings

import io.ferventio.app.domain.NotificationPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesCodecTest {
    @Test
    fun roundTripPreservesSparseGlobalAndChannelOverrides() {
        val preferences = NotificationPreferences()
            .withGlobalEvent("reply", false)
            .withChannelEnabled("channel-1", true)
            .withChannelEvent("channel-1", "reply", true)
            .withChannelMutedUntil("channel-1", 9_999_999L)
            .withChannelEnabled("channel-2", false)

        val restored = NotificationPreferencesCodec.decode(
            NotificationPreferencesCodec.encode(preferences),
        )

        assertEquals(preferences, restored)
        assertTrue(restored.isEnabled("reply", "channel-1"))
        assertEquals(
            9_999_999L,
            restored.channelOverrides.getValue("channel-1").mutedUntilEpochMillis,
        )
        assertFalse(restored.isEnabled("reply", "channel-2"))
    }

    @Test
    fun malformedPayloadFallsBackToDefaults() {
        assertEquals(
            NotificationPreferences(),
            NotificationPreferencesCodec.decode("{not-json"),
        )
    }
}
