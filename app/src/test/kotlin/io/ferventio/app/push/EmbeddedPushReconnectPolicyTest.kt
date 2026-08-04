package io.ferventio.app.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedPushReconnectPolicyTest {
    @Test
    fun backoffGrowsAndCapsAtOneMinute() {
        assertEquals(1_000L, EmbeddedPushReconnectPolicy.delayMillis(1, 0.0))
        assertEquals(2_000L, EmbeddedPushReconnectPolicy.delayMillis(2, 0.0))
        assertEquals(32_000L, EmbeddedPushReconnectPolicy.delayMillis(6, 0.0))
        assertEquals(60_000L, EmbeddedPushReconnectPolicy.delayMillis(20, 0.0))
    }

    @Test
    fun jitterIsClampedToTwentyPercent() {
        assertEquals(800L, EmbeddedPushReconnectPolicy.delayMillis(1, -1.0))
        assertEquals(1_200L, EmbeddedPushReconnectPolicy.delayMillis(1, 1.0))
        assertTrue(EmbeddedPushReconnectPolicy.delayMillis(1, -0.2) >= 500L)
    }
}
