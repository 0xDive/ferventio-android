package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchEventSubConnectionPolicyTest {
    @Test
    fun clampsKeepaliveAndAddsGracePeriod() {
        assertEquals(30, TwitchEventSubConnectionPolicy.keepaliveSeconds(null))
        assertEquals(10, TwitchEventSubConnectionPolicy.keepaliveSeconds(1))
        assertEquals(600, TwitchEventSubConnectionPolicy.keepaliveSeconds(1_000))
        assertEquals(40_000L, TwitchEventSubConnectionPolicy.receiveTimeoutMillis(30))
    }

    @Test
    fun matchesAndroidReconnectBackoff() {
        assertEquals(1_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(1))
        assertEquals(2_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(2))
        assertEquals(4_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(3))
        assertEquals(8_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(4))
        assertEquals(16_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(5))
        assertEquals(30_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(6))
        assertEquals(30_000L, TwitchEventSubConnectionPolicy.reconnectBaseDelayMillis(20))
    }

    @Test
    fun appliesBoundedPositiveJitter() {
        assertEquals(4_000L, TwitchEventSubConnectionPolicy.reconnectDelayMillis(3, 0.0))
        assertEquals(5_000L, TwitchEventSubConnectionPolicy.reconnectDelayMillis(3, 1.0))
        assertEquals(4_000L, TwitchEventSubConnectionPolicy.reconnectDelayMillis(3, -1.0))
        assertEquals(5_000L, TwitchEventSubConnectionPolicy.reconnectDelayMillis(3, 2.0))
    }

    @Test
    fun authorizationRevocationStopsAutomaticReconnect() {
        assertTrue(
            TwitchEventSubConnectionPolicy.shouldStopAfterRevocation("authorization_revoked"),
        )
        assertFalse(TwitchEventSubConnectionPolicy.shouldStopAfterRevocation("user_removed"))
        assertTrue(TwitchEventSubConnectionPolicy.canRetry(4))
        assertFalse(TwitchEventSubConnectionPolicy.canRetry(5))
    }
}
