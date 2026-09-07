package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchInteractiveRuntimeTest {
    @Test
    fun successfulPollMutationPublishesSnapshotAndClearsMutationStatus() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = FakeInteractiveGateway()
        gateway.createdPoll = poll(status = PollStatus.ACTIVE)
        val runtime = TwitchInteractiveRuntime(state, gateway)

        val result = runtime.createPoll(
            authentication = authentication(),
            broadcasterId = "broadcaster",
            draft = PollDraft("Question", listOf("A", "B"), 30),
        )

        assertEquals("poll", result.id)
        assertEquals(result, state.interactiveState.pollsByChannel["broadcaster"])
        assertFalse("broadcaster" in state.interactiveState.mutationsByChannel)
    }

    @Test
    fun unauthorizedMutationRequiresAuthenticationRefresh() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = FakeInteractiveGateway().apply {
            failure = TwitchInteractiveMutationException(
                operation = "Create poll",
                statusCode = 401,
                twitchMessage = "OAuth token is invalid",
            )
        }
        val runtime = TwitchInteractiveRuntime(state, gateway)

        assertFailsWith<TwitchInteractiveMutationException> {
            runtime.createPoll(
                authentication = authentication(),
                broadcasterId = "broadcaster",
                draft = PollDraft("Question", listOf("A", "B"), 30),
            )
        }

        val mutation = state.interactiveState.mutationsByChannel.getValue("broadcaster")
        assertEquals(InteractiveMutationFailureKind.AUTHENTICATION, mutation.failureKind)
        assertEquals(InteractiveMutationRecovery.RETRY, mutation.recovery)
        assertTrue(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
    }

    @Test
    fun permissionFailureDoesNotInvalidateAuthentication() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = FakeInteractiveGateway().apply {
            failure = TwitchInteractiveMutationException(
                operation = "Create prediction",
                statusCode = 403,
                twitchMessage = "Forbidden",
            )
        }
        val runtime = TwitchInteractiveRuntime(state, gateway)

        assertFailsWith<TwitchInteractiveMutationException> {
            runtime.createPrediction(
                authentication = authentication(),
                broadcasterId = "broadcaster",
                draft = PredictionDraft("Will it happen?", listOf("Yes", "No"), 60),
            )
        }

        val mutation = state.interactiveState.mutationsByChannel.getValue("broadcaster")
        assertEquals(InteractiveMutationFailureKind.PERMISSION, mutation.failureKind)
        assertEquals(InteractiveMutationRecovery.NONE, mutation.recovery)
        assertFalse(state.authenticationRequired)
    }

    @Test
    fun resolvingPredictionPublishesReturnedWinningSnapshot() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = FakeInteractiveGateway().apply {
            endedPrediction = prediction(
                status = PredictionStatus.RESOLVED,
                winningOutcomeId = "yes",
            )
        }
        val runtime = TwitchInteractiveRuntime(state, gateway)

        val result = runtime.endPrediction(
            authentication = authentication(),
            broadcasterId = "broadcaster",
            predictionId = "prediction",
            status = PredictionStatus.RESOLVED,
            winningOutcomeId = "yes",
        )

        assertEquals("yes", result.winningOutcomeId)
        assertEquals(result, state.interactiveState.predictionsByChannel["broadcaster"])
    }

    @Test
    fun foreignBroadcasterIsRejectedBeforeGatewayMutation() = runTest {
        val gateway = FakeInteractiveGateway()
        val runtime = TwitchInteractiveRuntime(ChatRuntimeStateHolder(), gateway)

        assertFailsWith<IllegalArgumentException> {
            runtime.createPoll(
                authentication = authentication(),
                broadcasterId = "other",
                draft = PollDraft("Question", listOf("A", "B"), 30),
            )
        }

        assertEquals(0, gateway.calls)
    }

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-token",
            expiresAtEpochMillis = 9_000_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "twitch-token",
            leaseExpiresAtEpochMillis = 2_000_000L,
            twitchExpiresAtEpochMillis = 8_000_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 9_000_000L,
            session = TwitchSession(
                clientId = "client-id",
                userId = "broadcaster",
                login = "broadcaster",
                scopes = setOf("channel:manage:polls", "channel:manage:predictions"),
                expiresInSeconds = 7_000L,
            ),
        ),
    )

    private fun poll(status: PollStatus) = PollOverlay(
        id = "poll",
        channelId = "broadcaster",
        title = "Question",
        choices = listOf(PollChoice("a", "A"), PollChoice("b", "B")),
        status = status,
        startedAtMillis = 1_000L,
        endsAtMillis = 31_000L,
        updatedAtMillis = 1_000L,
    )

    private fun prediction(
        status: PredictionStatus,
        winningOutcomeId: String? = null,
    ) = PredictionOverlay(
        id = "prediction",
        channelId = "broadcaster",
        title = "Will it happen?",
        outcomes = listOf(
            PredictionOutcome(id = "yes", title = "Yes"),
            PredictionOutcome(id = "no", title = "No"),
        ),
        status = status,
        startedAtMillis = 1_000L,
        winningOutcomeId = winningOutcomeId,
        updatedAtMillis = 2_000L,
    )

    private class FakeInteractiveGateway : TwitchInteractiveMutationGateway {
        var calls = 0
        var failure: Throwable? = null
        var createdPoll: PollOverlay = pollStatic()
        var endedPrediction: PredictionOverlay = predictionStatic()

        override suspend fun createPoll(
            authentication: StoredAuthentication,
            broadcasterId: String,
            draft: PollDraft,
        ): PollOverlay {
            calls += 1
            failure?.let { throw it }
            return createdPoll
        }

        override suspend fun endPoll(
            authentication: StoredAuthentication,
            broadcasterId: String,
            pollId: String,
            status: PollStatus,
        ): PollOverlay {
            calls += 1
            failure?.let { throw it }
            return createdPoll.copy(status = status, updatedAtMillis = 2_000L)
        }

        override suspend fun createPrediction(
            authentication: StoredAuthentication,
            broadcasterId: String,
            draft: PredictionDraft,
        ): PredictionOverlay {
            calls += 1
            failure?.let { throw it }
            return endedPrediction.copy(status = PredictionStatus.ACTIVE, winningOutcomeId = null)
        }

        override suspend fun endPrediction(
            authentication: StoredAuthentication,
            broadcasterId: String,
            predictionId: String,
            status: PredictionStatus,
            winningOutcomeId: String?,
        ): PredictionOverlay {
            calls += 1
            failure?.let { throw it }
            return endedPrediction
        }

        private companion object {
            fun pollStatic() = PollOverlay(
                id = "poll",
                channelId = "broadcaster",
                title = "Question",
                choices = listOf(PollChoice("a", "A"), PollChoice("b", "B")),
                status = PollStatus.ACTIVE,
                startedAtMillis = 1_000L,
                updatedAtMillis = 1_000L,
            )

            fun predictionStatic() = PredictionOverlay(
                id = "prediction",
                channelId = "broadcaster",
                title = "Will it happen?",
                outcomes = listOf(
                    PredictionOutcome(id = "yes", title = "Yes"),
                    PredictionOutcome(id = "no", title = "No"),
                ),
                status = PredictionStatus.RESOLVED,
                startedAtMillis = 1_000L,
                winningOutcomeId = "yes",
                updatedAtMillis = 2_000L,
            )
        }
    }
}
