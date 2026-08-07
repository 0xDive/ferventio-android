package io.ferventio.app.twitch

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchInteractiveEventSubParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `poll progress becomes active poll snapshot with vote counts`() {
        val event = event(
            """
            {
              "id":"poll-1",
              "broadcaster_user_id":"channel",
              "title":"Question",
              "choices":[
                {"id":"a","title":"A","bits_votes":0,"channel_points_votes":7,"votes":12},
                {"id":"b","title":"B","bits_votes":0,"channel_points_votes":4,"votes":14}
              ],
              "bits_voting":{"is_enabled":false,"amount_per_vote":0},
              "channel_points_voting":{"is_enabled":true,"amount_per_vote":10},
              "started_at":"2026-08-07T10:00:00Z",
              "ends_at":"2026-08-07T10:05:00Z"
            }
            """,
        )

        val parsed = TwitchInteractiveEventSubParser.parse(
            TwitchInteractiveEventSubParser.POLL_PROGRESS,
            event,
            observedAtMillis = 123L,
        ) as InteractiveChatOverlayEvent.PollSnapshot

        assertEquals(PollStatus.ACTIVE, parsed.poll.status)
        assertEquals(26, parsed.poll.totalVotes)
        assertTrue(parsed.poll.channelPointsVotingEnabled)
        assertEquals(10, parsed.poll.channelPointsPerVote)
        assertEquals(123L, parsed.poll.updatedAtMillis)
    }

    @Test
    fun `poll end preserves terminal status and ended time`() {
        val event = event(
            """
            {
              "id":"poll-1",
              "broadcaster_user_id":"channel",
              "title":"Question",
              "choices":[],
              "status":"terminated",
              "started_at":"2026-08-07T10:00:00Z",
              "ended_at":"2026-08-07T10:02:00Z"
            }
            """,
        )

        val parsed = TwitchInteractiveEventSubParser.parse(
            TwitchInteractiveEventSubParser.POLL_END,
            event,
            observedAtMillis = 5L,
        ) as InteractiveChatOverlayEvent.PollSnapshot

        assertEquals(PollStatus.TERMINATED, parsed.poll.status)
        assertEquals(1_786_096_920_000L, parsed.poll.endedAtMillis)
    }

    @Test
    fun `prediction progress becomes active snapshot`() {
        val event = event(
            """
            {
              "id":"prediction-1",
              "broadcaster_user_id":"channel",
              "title":"Will we win?",
              "outcomes":[
                {"id":"yes","title":"Yes","color":"blue","users":5,"channel_points":8000},
                {"id":"no","title":"No","color":"pink","users":2,"channel_points":2000}
              ],
              "started_at":"2026-08-07T10:00:00Z",
              "locks_at":"2026-08-07T10:02:00Z"
            }
            """,
        )

        val parsed = TwitchInteractiveEventSubParser.parse(
            TwitchInteractiveEventSubParser.PREDICTION_PROGRESS,
            event,
            observedAtMillis = 9L,
        ) as InteractiveChatOverlayEvent.PredictionSnapshot

        assertEquals(PredictionStatus.ACTIVE, parsed.prediction.status)
        assertEquals(7, parsed.prediction.totalUsers)
        assertEquals(10_000L, parsed.prediction.totalChannelPoints)
        assertEquals(1_786_096_920_000L, parsed.prediction.locksAtMillis)
    }

    @Test
    fun `prediction lock and end map lifecycle states`() {
        val locked = TwitchInteractiveEventSubParser.parse(
            TwitchInteractiveEventSubParser.PREDICTION_LOCK,
            event(
                """
                {
                  "id":"prediction-1","broadcaster_user_id":"channel","title":"Question",
                  "outcomes":[],"started_at":"2026-08-07T10:00:00Z",
                  "locked_at":"2026-08-07T10:02:00Z"
                }
                """,
            ),
            observedAtMillis = 10L,
        ) as InteractiveChatOverlayEvent.PredictionSnapshot

        val ended = TwitchInteractiveEventSubParser.parse(
            TwitchInteractiveEventSubParser.PREDICTION_END,
            event(
                """
                {
                  "id":"prediction-1","broadcaster_user_id":"channel","title":"Question",
                  "outcomes":[],"winning_outcome_id":"yes","status":"resolved",
                  "started_at":"2026-08-07T10:00:00Z","ended_at":"2026-08-07T10:05:00Z"
                }
                """,
            ),
            observedAtMillis = 11L,
        ) as InteractiveChatOverlayEvent.PredictionSnapshot

        assertEquals(PredictionStatus.LOCKED, locked.prediction.status)
        assertEquals(PredictionStatus.RESOLVED, ended.prediction.status)
        assertEquals("yes", ended.prediction.winningOutcomeId)
    }

    @Test
    fun `unknown subscription is ignored`() {
        val parsed = TwitchInteractiveEventSubParser.parse(
            "channel.chat.message",
            event("{}"),
            observedAtMillis = 1L,
        )

        assertNull(parsed)
    }

    private fun event(raw: String) = json.parseToJsonElement(raw.trimIndent()).jsonObject
}
