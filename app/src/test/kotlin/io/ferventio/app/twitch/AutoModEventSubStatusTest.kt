package io.ferventio.app.twitch

import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoModEventSubStatusTest {
    @Test
    fun `expired automod update is terminal and late hold is dropped`() {
        val update = EventSubParser.parseEnvelope(
            envelope(type = "automod.message.update", eventMessageId = "held-message-expired", status = "Expired"),
        )

        assertTrue(update.event is ChatEvent.AutoModUpdated)
        assertEquals(
            AutoModMessageStatus.DENIED,
            (update.event as ChatEvent.AutoModUpdated).message.status,
        )

        val lateHold = EventSubParser.parseEnvelope(
            envelope(type = "automod.message.hold", eventMessageId = "held-message-expired", status = null),
        )
        assertNull(lateHold.event)
    }

    private fun envelope(type: String, eventMessageId: String, status: String?): String {
        val statusField = status?.let { value -> ",\"status\":\"$value\"" }.orEmpty()
        return """
            {
              "metadata": {
                "message_id": "transport-$eventMessageId-$type",
                "message_type": "notification",
                "message_timestamp": "2026-08-08T10:00:00Z"
              },
              "payload": {
                "subscription": {"type": "$type"},
                "event": {
                  "broadcaster_user_id": "channel",
                  "broadcaster_user_login": "channel",
                  "broadcaster_user_name": "Channel",
                  "user_id": "viewer",
                  "user_login": "viewer",
                  "user_name": "Viewer",
                  "message_id": "$eventMessageId",
                  "message": {"text": "message"}$statusField
                }
              }
            }
        """.trimIndent()
    }
}
