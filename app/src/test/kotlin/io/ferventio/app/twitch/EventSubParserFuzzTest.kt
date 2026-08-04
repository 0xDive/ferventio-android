package io.ferventio.app.twitch

import io.ferventio.app.testing.DeterministicFuzzer
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubParserFuzzTest {
    @Test
    fun arbitraryBoundedPayloadsNeverThrowJvmErrors() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1304L)

        repeat(1_500) { iteration ->
            val raw = fuzz.text(maxLength = 8_192)
            try {
                EventSubParser.parseEnvelope(raw)
            } catch (_: Exception) {
                // Malformed input is expected. JVM Errors still escape and fail the test.
            }
        }
    }

    @Test
    fun randomizedJsonShapesRemainBounded() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1305L)
        val messageTypes = listOf(
            "session_welcome",
            "session_keepalive",
            "session_reconnect",
            "notification",
            "revocation",
            "unknown",
        )
        val subscriptionTypes = listOf(
            "channel.chat.message",
            "channel.chat.notification",
            "channel.chat.message_delete",
            "channel.chat.clear_user_messages",
            "channel.chat.clear",
            "automod.message.hold",
            "automod.message.update",
            "channel.moderate",
            "channel.chat_settings.update",
            "channel.unknown.event",
        )
        val eventShapes = listOf("{}", "null", "[]", "0", "true", "\"text\"")

        repeat(1_000) { iteration ->
            val type = fuzz.choose(messageTypes)
            val subscription = fuzz.choose(subscriptionTypes)
            val event = fuzz.choose(eventShapes)
            val raw = """
                {
                  "metadata": {
                    "message_id": "fuzz-$iteration",
                    "message_type": "$type",
                    "message_timestamp": "2026-07-31T00:00:00Z"
                  },
                  "payload": {
                    "subscription": {"type": "$subscription", "status": "fuzz"},
                    "event": $event,
                    "session": {"id": "session-$iteration", "keepalive_timeout_seconds": 10}
                  }
                }
            """.trimIndent()

            val envelope = EventSubParser.parseEnvelope(raw)
            assertEquals(type, envelope.type)
            assertEquals("fuzz-$iteration", envelope.messageId)
        }
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsJsonDepth() {
        val quotedBrackets = "[".repeat(256) + "]".repeat(256)
        val envelope = EventSubParser.parseEnvelope(
            """{"metadata":{"message_type":"session_keepalive","ignored":"$quotedBrackets"},"payload":{}}""",
        )
        assertEquals("session_keepalive", envelope.type)
    }

    @Test
    fun oversizedAndDeepPayloadsAreRejectedBeforeJsonParsing() {
        val oversized = "x".repeat(EventSubParser.MAX_EVENTSUB_ENVELOPE_CHARS + 1)
        assertFailsWith<IllegalArgumentException> { EventSubParser.parseEnvelope(oversized) }

        val deep = "[".repeat(EventSubParser.MAX_EVENTSUB_JSON_DEPTH + 1) +
            "0" + "]".repeat(EventSubParser.MAX_EVENTSUB_JSON_DEPTH + 1)
        val failure = assertFailsWith<IllegalArgumentException> { EventSubParser.parseEnvelope(deep) }
        assertTrue(failure.message.orEmpty().contains("вложенность"))
    }
}
