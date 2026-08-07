package io.ferventio.app.application

import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.twitch.PollEndStatus
import io.ferventio.app.twitch.PredictionEndStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveChatCoordinatorTest {
    private val auth = InteractiveChatAuth(
        clientId = "client",
        accessToken = "token",
        broadcasterId = "channel",
    )

    @Test
    fun `refresh hydrates active poll and locked prediction`() {
        runBlocking {
            val gateway = FakeGateway(
                polls = listOf(
                    poll("old", PollStatus.COMPLETED, updatedAt = 10L),
                    poll("active", PollStatus.ACTIVE, updatedAt = 20L),
                ),
                predictions = listOf(
                    prediction("locked", PredictionStatus.LOCKED, updatedAt = 30L),
                ),
            )
            val coordinator = InteractiveChatCoordinator(gateway)

            coordinator.refresh(auth)

            assertEquals("active", coordinator.state.value.pollsByChannel["channel"]?.id)
            assertEquals("locked", coordinator.state.value.predictionsByChannel["channel"]?.id)
        }
    }

    @Test
    fun `refresh clears stale overlays when Helix returns none`() {
        runBlocking {
            val gateway = FakeGateway()
            val coordinator = InteractiveChatCoordinator(gateway)
            coordinator.ingestPoll(poll("stale", PollStatus.ACTIVE, updatedAt = 1L))
            coordinator.ingestPrediction(prediction("stale", PredictionStatus.ACTIVE, updatedAt = 1L))

            coordinator.refresh(auth)

            assertFalse("channel" in coordinator.state.value.pollsByChannel)
            assertFalse("channel" in coordinator.state.value.predictionsByChannel)
        }
    }

    @Test
    fun `create and end operations ingest returned snapshots`() {
        runBlocking {
            val gateway = FakeGateway(
                createdPoll = poll("created", PollStatus.ACTIVE, updatedAt = 20L),
                endedPoll = poll("created", PollStatus.TERMINATED, updatedAt = 30L),
                createdPrediction = prediction("created-p", PredictionStatus.ACTIVE, updatedAt = 40L),
                endedPrediction = prediction("created-p", PredictionStatus.LOCKED, updatedAt = 50L),
            )
            val coordinator = InteractiveChatCoordinator(gateway)

            coordinator.createPoll(
                auth,
                PollDraft(title = "Question", choices = listOf("A", "B"), durationSeconds = 60),
            )
            assertEquals(PollStatus.ACTIVE, coordinator.state.value.pollsByChannel["channel"]?.status)

            coordinator.endPoll(auth, "created", PollEndStatus.TERMINATED)
            assertEquals(PollStatus.TERMINATED, coordinator.state.value.pollsByChannel["channel"]?.status)

            coordinator.createPrediction(
                auth,
                PredictionDraft(title = "Question", outcomes = listOf("A", "B"), predictionWindowSeconds = 60),
            )
            assertEquals(PredictionStatus.ACTIVE, coordinator.state.value.predictionsByChannel["channel"]?.status)

            coordinator.endPrediction(auth, "created-p", PredictionEndStatus.LOCKED)
            assertEquals(PredictionStatus.LOCKED, coordinator.state.value.predictionsByChannel["channel"]?.status)
        }
    }

    @Test
    fun `close releases gateway`() {
        val gateway = FakeGateway()
        val coordinator = InteractiveChatCoordinator(gateway)

        coordinator.close()

        assertTrue(gateway.closed)
    }

    private fun poll(id: String, status: PollStatus, updatedAt: Long): PollOverlay = PollOverlay(
        id = id,
        channelId = "channel",
        title = "Question",
        choices = listOf(PollChoice("a", "A", votes = 1), PollChoice("b", "B", votes = 2)),
        status = status,
        startedAtMillis = 1L,
        updatedAtMillis = updatedAt,
    )

    private fun prediction(id: String, status: PredictionStatus, updatedAt: Long): PredictionOverlay =
        PredictionOverlay(
            id = id,
            channelId = "channel",
            title = "Question",
            outcomes = listOf(PredictionOutcome("a", "A"), PredictionOutcome("b", "B")),
            status = status,
            startedAtMillis = 1L,
            updatedAtMillis = updatedAt,
        )

    private class FakeGateway(
        var polls: List<PollOverlay> = emptyList(),
        var predictions: List<PredictionOverlay> = emptyList(),
        var createdPoll: PollOverlay = PollOverlay(
            id = "poll",
            channelId = "channel",
            title = "Question",
            choices = emptyList(),
            status = PollStatus.ACTIVE,
            startedAtMillis = 1L,
            updatedAtMillis = 1L,
        ),
        var endedPoll: PollOverlay = createdPoll.copy(status = PollStatus.TERMINATED, updatedAtMillis = 2L),
        var createdPrediction: PredictionOverlay = PredictionOverlay(
            id = "prediction",
            channelId = "channel",
            title = "Question",
            outcomes = emptyList(),
            status = PredictionStatus.ACTIVE,
            startedAtMillis = 1L,
            updatedAtMillis = 1L,
        ),
        var endedPrediction: PredictionOverlay = createdPrediction.copy(
            status = PredictionStatus.LOCKED,
            updatedAtMillis = 2L,
        ),
    ) : InteractiveChatGateway {
        var closed: Boolean = false

        override suspend fun getPolls(auth: InteractiveChatAuth): List<PollOverlay> = polls

        override suspend fun createPoll(auth: InteractiveChatAuth, draft: PollDraft): PollOverlay = createdPoll

        override suspend fun endPoll(
            auth: InteractiveChatAuth,
            pollId: String,
            status: PollEndStatus,
        ): PollOverlay = endedPoll

        override suspend fun getPredictions(auth: InteractiveChatAuth): List<PredictionOverlay> = predictions

        override suspend fun createPrediction(
            auth: InteractiveChatAuth,
            draft: PredictionDraft,
        ): PredictionOverlay = createdPrediction

        override suspend fun endPrediction(
            auth: InteractiveChatAuth,
            predictionId: String,
            status: PredictionEndStatus,
            winningOutcomeId: String?,
        ): PredictionOverlay = endedPrediction

        override fun close() {
            closed = true
        }
    }
}
