package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveChatOverlayReducerTest {
    @Test
    fun `poll progress snapshot replaces older state`() {
        val initial = InteractiveChatOverlayState(
            pollsByChannel = mapOf("channel" to poll(updatedAt = 100L, votes = 1)),
        )

        val reduced = InteractiveChatOverlayReducer.reduce(
            initial,
            InteractiveChatOverlayEvent.PollSnapshot(poll(updatedAt = 200L, votes = 4)),
        )

        assertEquals(4, reduced.pollsByChannel.getValue("channel").totalVotes)
    }

    @Test
    fun `stale prediction snapshot cannot roll locked state backwards`() {
        val locked = prediction(status = PredictionStatus.LOCKED, updatedAt = 200L)
        val activeButStale = prediction(status = PredictionStatus.ACTIVE, updatedAt = 100L)
        val initial = InteractiveChatOverlayState(predictionsByChannel = mapOf("channel" to locked))

        val reduced = InteractiveChatOverlayReducer.reduce(
            initial,
            InteractiveChatOverlayEvent.PredictionSnapshot(activeButStale),
        )

        assertEquals(PredictionStatus.LOCKED, reduced.predictionsByChannel.getValue("channel").status)
    }

    @Test
    fun `poll and prediction can coexist in same channel`() {
        var state = InteractiveChatOverlayState()
        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.PollSnapshot(poll(updatedAt = 100L, votes = 2)),
        )
        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.PredictionSnapshot(
                prediction(status = PredictionStatus.ACTIVE, updatedAt = 100L),
            ),
        )

        assertTrue("channel" in state.pollsByChannel)
        assertTrue("channel" in state.predictionsByChannel)
    }

    @Test
    fun `mutation lifecycle is tracked per channel`() {
        var state = InteractiveChatOverlayState()
        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.MutationStarted(
                channelId = "channel",
                kind = InteractiveMutationKind.CANCEL_PREDICTION,
            ),
        )
        assertTrue(state.mutationsByChannel.getValue("channel").inFlight)

        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.MutationFailed(
                channelId = "channel",
                kind = InteractiveMutationKind.CANCEL_PREDICTION,
            ),
        )
        val failed = state.mutationsByChannel.getValue("channel")
        assertTrue(failed.failed)
        assertTrue(!failed.inFlight)

        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.MutationStarted(
                channelId = "channel",
                kind = InteractiveMutationKind.LOCK_PREDICTION,
            ),
        )
        state = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.MutationSucceeded(
                channelId = "channel",
                kind = InteractiveMutationKind.LOCK_PREDICTION,
            ),
        )
        assertTrue(state.mutationsByChannel.isEmpty())
    }

    @Test
    fun `clear channel removes both overlays`() {
        val state = InteractiveChatOverlayState(
            pollsByChannel = mapOf("channel" to poll(updatedAt = 100L, votes = 1)),
            predictionsByChannel = mapOf(
                "channel" to prediction(status = PredictionStatus.ACTIVE, updatedAt = 100L),
            ),
            mutationsByChannel = mapOf(
                "channel" to InteractiveMutationStatus(
                    kind = InteractiveMutationKind.END_POLL,
                    inFlight = false,
                    failed = true,
                ),
            ),
        )

        val reduced = InteractiveChatOverlayReducer.reduce(
            state,
            InteractiveChatOverlayEvent.ClearChannel("channel"),
        )

        assertTrue(reduced.pollsByChannel.isEmpty())
        assertTrue(reduced.predictionsByChannel.isEmpty())
        assertTrue(reduced.mutationsByChannel.isEmpty())
    }

    @Test
    fun `poll vote share is based on total votes`() {
        val poll = PollOverlay(
            id = "poll",
            channelId = "channel",
            title = "Question",
            choices = listOf(
                PollChoice("a", "A", votes = 3),
                PollChoice("b", "B", votes = 1),
            ),
            status = PollStatus.ACTIVE,
            startedAtMillis = 0L,
            updatedAtMillis = 1L,
        )

        assertEquals(0.75, poll.voteShare("a"), 0.0001)
    }

    @Test
    fun `prediction points share is based on channel points`() {
        val prediction = PredictionOverlay(
            id = "prediction",
            channelId = "channel",
            title = "Question",
            outcomes = listOf(
                PredictionOutcome("a", "A", channelPoints = 8_000L),
                PredictionOutcome("b", "B", channelPoints = 2_000L),
            ),
            status = PredictionStatus.ACTIVE,
            startedAtMillis = 0L,
            updatedAtMillis = 1L,
        )

        assertEquals(0.8, prediction.pointsShare("a"), 0.0001)
    }

    @Test
    fun `poll draft validator enforces Twitch limits`() {
        val errors = InteractiveOverlayDraftValidator.validatePoll(
            PollDraft(
                title = "",
                choices = listOf("A"),
                durationSeconds = 5,
                channelPointsVotingEnabled = true,
                channelPointsPerVote = 0,
            ),
        )

        assertEquals(4, errors.size)
    }

    @Test
    fun `prediction draft validator accepts valid draft`() {
        val errors = InteractiveOverlayDraftValidator.validatePrediction(
            PredictionDraft(
                title = "Will we win?",
                outcomes = listOf("Yes", "No"),
                predictionWindowSeconds = 120,
            ),
        )

        assertTrue(errors.isEmpty())
    }

    private fun poll(updatedAt: Long, votes: Int): PollOverlay = PollOverlay(
        id = "poll",
        channelId = "channel",
        title = "Question",
        choices = listOf(PollChoice("a", "A", votes = votes)),
        status = PollStatus.ACTIVE,
        startedAtMillis = 0L,
        updatedAtMillis = updatedAt,
    )

    private fun prediction(status: PredictionStatus, updatedAt: Long): PredictionOverlay = PredictionOverlay(
        id = "prediction",
        channelId = "channel",
        title = "Question",
        outcomes = listOf(
            PredictionOutcome("a", "A", users = 2, channelPoints = 100L),
            PredictionOutcome("b", "B", users = 1, channelPoints = 50L),
        ),
        status = status,
        startedAtMillis = 0L,
        updatedAtMillis = updatedAt,
    )
}
