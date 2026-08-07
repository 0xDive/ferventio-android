package io.ferventio.app.twitch

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubInteractiveEnvelopeTest {
    @Test
    fun `poll notification exposes typed interactive event`() {
        val envelope = EventSubParser.parseEnvelope(
            notification(
                type = TwitchInteractiveEventSubParser.POLL_PROGRESS,
                event = """
                    {
                      "id":"poll-1",
                      "broadcaster_user_id":"channel-1",
                      "title":"Pick one",
                      "choices":[{"id":"a","title":"A","votes":7}],
                      "started_at":"2026-08-07T10:00:00Z",
                      "ends_at":"2026-08-07T10:05:00Z"
                    }
                """.trimIndent(),
            ),
        )

        assertNull(envelope.event)
        assertTrue(envelope.interactiveEvent is InteractiveChatOverlayEvent.PollSnapshot)
        val poll = (envelope.interactiveEvent as InteractiveChatOverlayEvent.PollSnapshot).poll
        assertEquals("poll-1", poll.id)
        assertEquals("channel-1", poll.channelId)
        assertEquals(PollStatus.ACTIVE, poll.status)
        assertEquals(7, poll.choices.single().votes)
    }

    @Test
    fun `prediction notification exposes typed interactive event`() {
        val envelope = EventSubParser.parseEnvelope(
            notification(
                type = TwitchInteractiveEventSubParser.PREDICTION_LOCK,
                event = """
                    {
                      "id":"prediction-1",
                      "broadcaster_user_id":"channel-1",
                      "title":"Will it happen?",
                      "outcomes":[{"id":"yes","title":"Yes","users":3,"channel_points":120,"color":"blue"}],
                      "started_at":"2026-08-07T10:00:00Z",
                      "locked_at":"2026-08-07T10:02:00Z"
                    }
                """.trimIndent(),
            ),
        )

        assertNull(envelope.event)
        assertTrue(envelope.interactiveEvent is InteractiveChatOverlayEvent.PredictionSnapshot)
        val prediction = (envelope.interactiveEvent as InteractiveChatOverlayEvent.PredictionSnapshot).prediction
        assertEquals("prediction-1", prediction.id)
        assertEquals(PredictionStatus.LOCKED, prediction.status)
        assertEquals(120L, prediction.outcomes.single().channelPoints)
    }

    @Test
    fun `ordinary chat notification has no interactive event`() {
        val envelope = EventSubParser.parseEnvelope(
            notification(
                type = "channel.chat.clear",
                event = """{"broadcaster_user_id":"channel-1"}""",
            ),
        )

        assertTrue(envelope.event != null)
        assertNull(envelope.interactiveEvent)
    }

    private fun notification(type: String, event: String): String = """
        {
          "metadata": {
            "message_id":"event-1",
            "message_type":"notification",
            "message_timestamp":"2026-08-07T10:03:00Z"
          },
          "payload": {
            "subscription": {"type":"$type"},
            "event": $event
          }
        }
    """.trimIndent()
}
