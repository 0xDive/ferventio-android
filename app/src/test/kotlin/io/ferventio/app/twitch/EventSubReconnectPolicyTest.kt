package io.ferventio.app.twitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubReconnectPolicyTest {
    @Test
    fun exponentialBackoffIsCapped() {
        assertEquals(1_000L, EventSubReconnectPolicy.baseDelayMillis(1))
        assertEquals(2_000L, EventSubReconnectPolicy.baseDelayMillis(2))
        assertEquals(4_000L, EventSubReconnectPolicy.baseDelayMillis(3))
        assertEquals(8_000L, EventSubReconnectPolicy.baseDelayMillis(4))
        assertEquals(16_000L, EventSubReconnectPolicy.baseDelayMillis(5))
        assertEquals(30_000L, EventSubReconnectPolicy.baseDelayMillis(6))
        assertEquals(30_000L, EventSubReconnectPolicy.baseDelayMillis(20))
    }

    @Test
    fun jitterAddsAtMostTwentyFivePercent() {
        val minimum = EventSubReconnectPolicy.delayMillis(3, 0.0)
        val maximum = EventSubReconnectPolicy.delayMillis(3, 1.0)

        assertEquals(4_000L, minimum)
        assertEquals(5_000L, maximum)
        assertTrue(maximum >= minimum)
    }
}
