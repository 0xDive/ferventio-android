package io.ferventio.shared.chat

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TwitchEventSubProtocolParserTest {
    @Test
    fun parsesWelcomeSessionAndKeepaliveTimeout() {
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {"message_type": "session_welcome"},
              "payload": {
                "session": {
                  "id": "session-1",
                  "keepalive_timeout_seconds": 30,
                  "reconnect_url": null
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("session_welcome", envelope.type)
        assertEquals("session-1", envelope.sessionId)
        assertEquals(30, envelope.keepaliveTimeoutSeconds)
        assertNull(envelope.reconnectUrl)
    }

    @Test
    fun parsesReconnectUrl() {
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {"message_type": "session_reconnect"},
              "payload": {
                "session": {
                  "id": "session-2",
                  "keepalive_timeout_seconds": null,
                  "reconnect_url": "wss://eventsub.wss.twitch.tv/ws?reconnect=test"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("session_reconnect", envelope.type)
        assertEquals("session-2", envelope.sessionId)
        assertEquals(
            "wss://eventsub.wss.twitch.tv/ws?reconnect=test",
            envelope.reconnectUrl,
        )
    }

    @Test
    fun parsesKeepaliveMetadata() {
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {
                "message_id": "keepalive-1",
                "message_type": "session_keepalive",
                "message_timestamp": "2026-07-21T18:00:00Z"
              },
              "payload": {}
            }
            """.trimIndent(),
        )

        assertEquals("session_keepalive", envelope.type)
        assertEquals("keepalive-1", envelope.messageId)
        assertEquals("2026-07-21T18:00:00Z", envelope.messageTimestamp)
    }

    @Test
    fun parsesRevocationReason() {
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {
                "message_id": "revocation-1",
                "message_type": "revocation"
              },
              "payload": {
                "subscription": {
                  "type": "channel.chat.message",
                  "status": "authorization_revoked"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("revocation", envelope.type)
        assertEquals("channel.chat.message", envelope.subscriptionType)
        assertEquals("authorization_revoked", envelope.revocationStatus)
    }

    @Test
    fun notificationKeepsRawPayloadForDomainParser() {
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {
                "message_id": "notification-1",
                "message_type": "notification",
                "message_timestamp": "2026-08-16T16:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "message_id": "message-1"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("notification", envelope.type)
        assertEquals("channel.chat.message", envelope.subscriptionType)
        assertEquals(
            "message-1",
            envelope.eventPayload?.get("message_id")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun rejectsOversizedEnvelopeBeforeJsonParsing() {
        assertFailsWith<IllegalArgumentException> {
            TwitchEventSubProtocolParser.parse(
                " ".repeat(TwitchEventSubProtocolParser.MAX_ENVELOPE_CHARS + 1),
            )
        }
    }
}
