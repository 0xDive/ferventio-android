package io.ferventio.app.application

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.InteractiveChatOverlayReducer
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationKind
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.twitch.PollEndStatus
import io.ferventio.app.twitch.PredictionEndStatus
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class InteractiveChatAuth(
    val clientId: String,
    val accessToken: String,
    val broadcasterId: String,
)

private sealed interface InteractiveRetryCommand {
    val kind: InteractiveMutationKind

    data class CreatePoll(val draft: PollDraft) : InteractiveRetryCommand {
        override val kind = InteractiveMutationKind.CREATE_POLL
    }

    data class EndPoll(
        val pollId: String,
        val status: PollEndStatus,
    ) : InteractiveRetryCommand {
        override val kind = when (status) {
            PollEndStatus.TERMINATED -> InteractiveMutationKind.END_POLL
            PollEndStatus.ARCHIVED -> InteractiveMutationKind.ARCHIVE_POLL
        }
    }

    data class CreatePrediction(val draft: PredictionDraft) : InteractiveRetryCommand {
        override val kind = InteractiveMutationKind.CREATE_PREDICTION
    }

    data class EndPrediction(
        val predictionId: String,
        val status: PredictionEndStatus,
        val winningOutcomeId: String?,
    ) : InteractiveRetryCommand {
        override val kind = when (status) {
            PredictionEndStatus.LOCKED -> InteractiveMutationKind.LOCK_PREDICTION
            PredictionEndStatus.CANCELED -> InteractiveMutationKind.CANCEL_PREDICTION
            PredictionEndStatus.RESOLVED -> InteractiveMutationKind.RESOLVE_PREDICTION
        }
    }
}

/** Granular state holder and Twitch API boundary for Polls / Predictions. */
class InteractiveChatCoordinator internal constructor(
    private val api: InteractiveChatGateway = TwitchInteractiveChatGateway(),
) : Closeable {
    private val mutableState = MutableStateFlow(InteractiveChatOverlayState())
    val state: StateFlow<InteractiveChatOverlayState> = mutableState.asStateFlow()
    private val mutationMutex = Mutex()
    private val pendingRetries = ConcurrentHashMap<String, InteractiveRetryCommand>()

    suspend fun refresh(auth: InteractiveChatAuth) = mutationMutex.withLock {
        val (polls, predictions) = coroutineScope {
            val pollsDeferred = async { api.getPolls(auth) }
            val predictionsDeferred = async { api.getPredictions(auth) }
            pollsDeferred.await() to predictionsDeferred.await()
        }

        val poll = polls.firstOrNull { it.status == PollStatus.ACTIVE }
            ?: polls.firstOrNull {
                it.status == PollStatus.COMPLETED ||
                    it.status == PollStatus.TERMINATED ||
                    it.status == PollStatus.ARCHIVED
            }
        val prediction = predictions.firstOrNull {
            it.status == PredictionStatus.ACTIVE || it.status == PredictionStatus.LOCKED
        } ?: predictions.firstOrNull { it.status == PredictionStatus.RESOLVED }

        pendingRetries.remove(auth.broadcasterId)
        mutableState.update { current ->
            var next = InteractiveChatOverlayReducer.reduce(
                current,
                InteractiveChatOverlayEvent.ClearChannel(auth.broadcasterId),
            )
            if (poll != null) {
                next = InteractiveChatOverlayReducer.reduce(
                    next,
                    InteractiveChatOverlayEvent.PollSnapshot(poll),
                )
            }
            if (prediction != null) {
                next = InteractiveChatOverlayReducer.reduce(
                    next,
                    InteractiveChatOverlayEvent.PredictionSnapshot(prediction),
                )
            }
            next
        }
    }

    suspend fun createPoll(auth: InteractiveChatAuth, draft: PollDraft): PollOverlay {
        val command = InteractiveRetryCommand.CreatePoll(draft)
        return runMutation(auth, command) { api.createPoll(auth, draft).also(::ingestPoll) }
    }

    suspend fun endPoll(
        auth: InteractiveChatAuth,
        pollId: String,
        status: PollEndStatus,
    ): PollOverlay {
        val command = InteractiveRetryCommand.EndPoll(pollId, status)
        return runMutation(auth, command) { api.endPoll(auth, pollId, status).also(::ingestPoll) }
    }

    suspend fun createPrediction(
        auth: InteractiveChatAuth,
        draft: PredictionDraft,
    ): PredictionOverlay {
        val command = InteractiveRetryCommand.CreatePrediction(draft)
        return runMutation(auth, command) { api.createPrediction(auth, draft).also(::ingestPrediction) }
    }

    suspend fun endPrediction(
        auth: InteractiveChatAuth,
        predictionId: String,
        status: PredictionEndStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay {
        val command = InteractiveRetryCommand.EndPrediction(predictionId, status, winningOutcomeId)
        return runMutation(auth, command) {
            api.endPrediction(auth, predictionId, status, winningOutcomeId).also(::ingestPrediction)
        }
    }

    suspend fun recover(auth: InteractiveChatAuth): Boolean {
        val mutation = state.value.mutationsByChannel[auth.broadcasterId] ?: return false
        return when (mutation.recovery) {
            InteractiveMutationRecovery.NONE -> false
            InteractiveMutationRecovery.REFRESH -> recoverByRefresh(auth, mutation)
            InteractiveMutationRecovery.RETRY -> {
                val command = pendingRetries[auth.broadcasterId] ?: return false
                runMutation(auth, command) { executeRetryCommand(auth, command) }
                true
            }
        }
    }

    private suspend fun recoverByRefresh(
        auth: InteractiveChatAuth,
        mutation: io.ferventio.app.domain.InteractiveMutationStatus,
    ): Boolean {
        val channelId = auth.broadcasterId
        val failureKind = mutation.failureKind ?: InteractiveMutationFailureKind.UNKNOWN
        ingest(InteractiveChatOverlayEvent.MutationStarted(channelId, mutation.kind))
        return try {
            refresh(auth)
            true
        } catch (error: Throwable) {
            ingest(
                InteractiveChatOverlayEvent.MutationFailed(
                    channelId = channelId,
                    kind = mutation.kind,
                    failureKind = failureKind,
                    recovery = InteractiveMutationRecovery.REFRESH,
                ),
            )
            throw error
        }
    }

    private suspend fun executeRetryCommand(
        auth: InteractiveChatAuth,
        command: InteractiveRetryCommand,
    ) {
        when (command) {
            is InteractiveRetryCommand.CreatePoll -> api.createPoll(auth, command.draft).also(::ingestPoll)
            is InteractiveRetryCommand.EndPoll -> api.endPoll(auth, command.pollId, command.status).also(::ingestPoll)
            is InteractiveRetryCommand.CreatePrediction -> api.createPrediction(auth, command.draft).also(::ingestPrediction)
            is InteractiveRetryCommand.EndPrediction -> api.endPrediction(
                auth = auth,
                predictionId = command.predictionId,
                status = command.status,
                winningOutcomeId = command.winningOutcomeId,
            ).also(::ingestPrediction)
        }
    }

    private suspend fun <T> runMutation(
        auth: InteractiveChatAuth,
        command: InteractiveRetryCommand,
        block: suspend () -> T,
    ): T = mutationMutex.withLock {
        val channelId = auth.broadcasterId
        ingest(InteractiveChatOverlayEvent.MutationStarted(channelId, command.kind))
        try {
            block().also {
                pendingRetries.remove(channelId)
                ingest(InteractiveChatOverlayEvent.MutationSucceeded(channelId, command.kind))
            }
        } catch (cancelled: CancellationException) {
            pendingRetries.remove(channelId)
            ingest(
                InteractiveChatOverlayEvent.MutationFailed(
                    channelId = channelId,
                    kind = command.kind,
                    failureKind = InteractiveMutationFailureKind.UNKNOWN,
                    recovery = InteractiveMutationRecovery.REFRESH,
                ),
            )
            throw cancelled
        } catch (error: Throwable) {
            val failure = InteractiveMutationFailureClassifier.classify(error)
            if (failure.recovery == InteractiveMutationRecovery.RETRY) {
                pendingRetries[channelId] = command
            } else {
                pendingRetries.remove(channelId)
            }
            ingest(
                InteractiveChatOverlayEvent.MutationFailed(
                    channelId = channelId,
                    kind = command.kind,
                    failureKind = failure.kind,
                    recovery = failure.recovery,
                ),
            )
            throw error
        }
    }

    fun ingest(event: InteractiveChatOverlayEvent) {
        mutableState.update { current -> InteractiveChatOverlayReducer.reduce(current, event) }
    }

    fun ingestPoll(poll: PollOverlay) = ingest(InteractiveChatOverlayEvent.PollSnapshot(poll))

    fun ingestPrediction(prediction: PredictionOverlay) =
        ingest(InteractiveChatOverlayEvent.PredictionSnapshot(prediction))

    fun clearChannel(channelId: String) {
        pendingRetries.remove(channelId)
        ingest(InteractiveChatOverlayEvent.ClearChannel(channelId))
    }

    override fun close() {
        pendingRetries.clear()
        api.close()
    }
}
