package io.ferventio.app.twitch

import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchInteractiveApiParserTest {
    @Test
    fun `parses active poll and derives scheduled end`() {
        val body = """
            {
              "data": [{
                "id": "poll-1",
                "broadcaster_id": "channel",
                "title": "Heads or Tails?",
                "choices": [
                  {"id":"heads","title":"Heads","votes":7,"channel_points_votes":2,"bits_votes":0},
                  {"id":"tails","title":"Tails","votes":3,"channel_points_votes":1,"bits_votes":0}
                ],
                "bits_voting_enabled": false,
                "bits_per_vote": 0,
                "channel_points_voting_enabled": true,
                "channel_points_per_vote": 100,
                "status": "ACTIVE",
                "duration": 300,
                "started_at": "2026-08-07T10:00:00Z"
              }]
            }
        """.trimIndent()

        val poll = TwitchInteractiveApiParser.parsePollResponse(body, updatedAtMillis = 99L).single()

        assertEquals("poll-1", poll.id)
        assertEquals(PollStatus.ACTIVE, poll.status)
        assertEquals(10, poll.totalVotes)
        assertTrue(poll.channelPointsVotingEnabled)
        assertFalse(poll.bitsVotingEnabled)
        assertEquals(100, poll.channelPointsPerVote)
        assertEquals(1_786_097_100_000L, poll.endsAtMillis)
        assertEquals(99L, poll.updatedAtMillis)
    }

    @Test
    fun `preserves moderated and invalid Twitch poll terminal states`() {
        val moderated = pollWithStatus("MODERATED")
        val invalid = pollWithStatus("INVALID")

        assertEquals(
            PollStatus.MODERATED,
            TwitchInteractiveApiParser.parsePollResponse(moderated, 1L).single().status,
        )
        assertEquals(
            PollStatus.INVALID,
            TwitchInteractiveApiParser.parsePollResponse(invalid, 1L).single().status,
        )
    }

    @Test
    fun `parses locked prediction including points and timestamps`() {
        val body = """
            {
              "data": [{
                "id": "prediction-1",
                "broadcaster_id": "channel",
                "title": "Will we win?",
                "winning_outcome_id": null,
                "outcomes": [
                  {"id":"yes","title":"Yes","users":5,"channel_points":8000,"color":"BLUE"},
                  {"id":"no","title":"No","users":2,"channel_points":2000,"color":"PINK"}
                ],
                "prediction_window": 120,
                "status": "LOCKED",
                "created_at": "2026-08-07T10:00:00Z",
                "locked_at": "2026-08-07T10:01:30Z",
                "ended_at": null
              }]
            }
        """.trimIndent()

        val prediction = TwitchInteractiveApiParser.parsePredictionResponse(body, updatedAtMillis = 50L).single()

        assertEquals(PredictionStatus.LOCKED, prediction.status)
        assertEquals(7, prediction.totalUsers)
        assertEquals(10_000L, prediction.totalChannelPoints)
        assertEquals(PredictionOutcomeColor.BLUE, prediction.outcomes.first().color)
        assertEquals(PredictionOutcomeColor.PINK, prediction.outcomes.last().color)
        assertEquals(1_786_096_920_000L, prediction.locksAtMillis)
        assertEquals(1_786_096_890_000L, prediction.lockedAtMillis)
        assertNull(prediction.endedAtMillis)
        assertNull(prediction.winningOutcomeId)
    }

    @Test
    fun `unknown statuses are retained as safe unknown values`() {
        val prediction = """
            {"data":[{
              "id":"p","broadcaster_id":"channel","title":"Q",
              "outcomes":[],"prediction_window":30,"status":"SOMETHING_NEW",
              "created_at":"2026-08-07T10:00:00Z"
            }]}
        """.trimIndent()

        assertEquals(
            PredictionStatus.UNKNOWN,
            TwitchInteractiveApiParser.parsePredictionResponse(prediction, 1L).single().status,
        )
    }

    @Test
    fun `empty Helix data returns empty overlays`() {
        assertTrue(TwitchInteractiveApiParser.parsePollResponse("{\"data\":[]}", 1L).isEmpty())
        assertTrue(TwitchInteractiveApiParser.parsePredictionResponse("{\"data\":[]}", 1L).isEmpty())
    }

    private fun pollWithStatus(status: String): String = """
        {"data":[{
          "id":"poll","broadcaster_id":"channel","title":"Q","choices":[],
          "status":"$status","duration":30,"started_at":"2026-08-07T10:00:00Z"
        }]}
    """.trimIndent()
}
