package io.ferventio.shared.chat

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TwitchInteractiveEventParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesPollProgress() {
        val event = objectOf(
            """
            {
              "id":"poll-1",
              "broadcaster_user_id":"channel-1",
              "title":"Best option?",
              "choices":[
                {"id":"a","title":"A","votes":12,"channel_points_votes":7,"bits_votes":5},
                {"id":"b","title":"B","votes":8,"channel_points_votes":8,"bits_votes":0}
              ],
              "channel_points_voting":{"is_enabled":true,"amount_per_vote":10},
              "bits_voting":{"is_enabled":true,"amount_per_vote":5},
              "started_at":"2026-08-17T10:00:00Z",
              "ends_at":"2026-08-17T10:01:00Z"
            }
            """,
        )

        val parsed = TwitchInteractiveEventParser.parseEvent(
            "channel.poll.progress",
            event,
            "2026-08-17T10:00:30Z",
        ) as InteractiveChatOverlayEvent.PollSnapshot

        assertEquals(PollStatus.ACTIVE, parsed.poll.status)
        assertEquals(20, parsed.poll.totalVotes)
        assertEquals(7, parsed.poll.choices.first().channelPointsVotes)
        assertEquals(10, parsed.poll.channelPointsPerVote)
    }

    @Test
    fun parsesPollEndStatus() {
        val event = objectOf(
            """
            {
              "id":"poll-1",
              "broadcaster_user_id":"channel-1",
              "title":"Done",
              "choices":[{"id":"a","title":"A","votes":3}],
              "status":"completed",
              "started_at":"2026-08-17T10:00:00Z",
              "ended_at":"2026-08-17T10:01:00Z"
            }
            """,
        )

        val parsed = TwitchInteractiveEventParser.parseEvent(
            "channel.poll.end",
            event,
            "2026-08-17T10:01:00Z",
        ) as InteractiveChatOverlayEvent.PollSnapshot

        assertEquals(PollStatus.COMPLETED, parsed.poll.status)
        assertEquals(3, parsed.poll.totalVotes)
    }

    @Test
    fun parsesPredictionLockAndEnd() {
        val common = """
            "id":"prediction-1",
            "broadcaster_user_id":"channel-1",
            "title":"Will it happen?",
            "outcomes":[
              {"id":"blue","title":"Yes","color":"blue","users":10,"channel_points":1200},
              {"id":"pink","title":"No","color":"pink","users":5,"channel_points":800}
            ],
            "started_at":"2026-08-17T10:00:00Z"
        """.trimIndent()
        val lock = objectOf("{$common," + "\"locked_at\":\"2026-08-17T10:02:00Z\"}")
        val ended = objectOf(
            "{$common," +
                "\"status\":\"resolved\",\"winning_outcome_id\":\"blue\"," +
                "\"ended_at\":\"2026-08-17T10:03:00Z\"}",
        )

        val locked = TwitchInteractiveEventParser.parseEvent(
            "channel.prediction.lock",
            lock,
            "2026-08-17T10:02:00Z",
        ) as InteractiveChatOverlayEvent.PredictionSnapshot
        val resolved = TwitchInteractiveEventParser.parseEvent(
            "channel.prediction.end",
            ended,
            "2026-08-17T10:03:00Z",
        ) as InteractiveChatOverlayEvent.PredictionSnapshot

        assertEquals(PredictionStatus.LOCKED, locked.prediction.status)
        assertEquals(2_000L, locked.prediction.totalChannelPoints)
        assertEquals(PredictionOutcomeColor.BLUE, locked.prediction.outcomes.first().color)
        assertEquals(PredictionStatus.RESOLVED, resolved.prediction.status)
        assertEquals("blue", resolved.prediction.winningOutcomeId)
    }

    @Test
    fun ignoresUnrelatedSubscriptionType() {
        assertNull(
            TwitchInteractiveEventParser.parseEvent(
                "channel.chat.message",
                objectOf("{}"),
                "2026-08-17T10:00:00Z",
            ),
        )
    }

    private fun objectOf(value: String) = json.parseToJsonElement(value).jsonObject
}
