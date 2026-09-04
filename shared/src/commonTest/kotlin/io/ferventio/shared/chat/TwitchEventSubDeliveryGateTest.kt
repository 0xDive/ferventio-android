package io.ferventio.shared.chat

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchEventSubDeliveryGateTest {
    @Test
    fun duplicateNotificationsAndRevocationsAreSuppressed() {
        val gate = TwitchEventSubDeliveryGate()

        assertTrue(gate.shouldDeliver(envelope("notification", "message-1"), 1_000L))
        assertFalse(gate.shouldDeliver(envelope("notification", "message-1"), 1_001L))
        assertTrue(gate.shouldDeliver(envelope("revocation", "message-2"), 2_000L))
        assertFalse(gate.shouldDeliver(envelope("revocation", "message-2"), 2_001L))
    }

    @Test
    fun lifecycleFramesRemainObservableWhenMessageIdRepeats() {
        val gate = TwitchEventSubDeliveryGate()

        assertTrue(gate.shouldDeliver(envelope("session_keepalive", "keepalive-1"), 1_000L))
        assertTrue(gate.shouldDeliver(envelope("session_keepalive", "keepalive-1"), 1_001L))
        assertTrue(gate.shouldDeliver(envelope("session_reconnect", "reconnect-1"), 2_000L))
        assertTrue(gate.shouldDeliver(envelope("session_reconnect", "reconnect-1"), 2_001L))
    }

    @Test
    fun expiredMessageIdCanBeDeliveredAgain() {
        val gate = TwitchEventSubDeliveryGate(ttlMillis = 10L)
        val envelope = envelope("notification", "message-1")

        assertTrue(gate.shouldDeliver(envelope, 100L))
        assertFalse(gate.shouldDeliver(envelope, 110L))
        assertTrue(gate.shouldDeliver(envelope, 111L))
    }

    @Test
    fun oldestIdsAreEvictedAtCapacity() {
        val gate = TwitchEventSubDeliveryGate(maxIds = 2, ttlMillis = 1_000L)

        assertTrue(gate.shouldDeliver(envelope("notification", "one"), 1L))
        assertTrue(gate.shouldDeliver(envelope("notification", "two"), 2L))
        assertTrue(gate.shouldDeliver(envelope("notification", "three"), 3L))
        assertTrue(gate.shouldDeliver(envelope("notification", "one"), 4L))
    }

    private fun envelope(type: String, messageId: String) = TwitchEventSubProtocolEnvelope(
        type = type,
        messageId = messageId,
        eventPayload = JsonObject(emptyMap()),
    )
}
