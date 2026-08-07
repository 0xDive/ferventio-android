package io.ferventio.app.application

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.InteractiveChatOverlayReducer
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.twitch.PollEndStatus
import io.ferventio.app.twitch.PredictionEndStatus
import java.io.Closeable
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

/**
 * Granular state holder for Polls / Predictions. It intentionally lives next to
 * the main controller instead of inside it so interactive overlays have their
 * own lifecycle, tests and Twitch API boundary.
 */
class InteractiveChatCoordinator internal constructor(
    private val api: InteractiveChatGateway = TwitchInteractiveChatGateway(),
) : Closeable {
    private val mutableState = MutableStateFlow(InteractiveChatOverlayState())
    val state: StateFlow<InteractiveChatOverlayState> = mutableState.asStateFlow()
    private val mutationMutex = Mutex()

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

    suspend fun createPoll(auth: InteractiveChatAuth, draft: PollDraft): PollOverlay =
        mutationMutex.withLock {
            api.createPoll(auth, draft).also(::ingestPoll)
        }

    suspend fun endPoll(
        auth: InteractiveChatAuth,
        pollId: String,
        status: PollEndStatus,
    ): PollOverlay = mutationMutex.withLock {
        api.endPoll(auth, pollId, status).also(::ingestPoll)
    }

    suspend fun createPrediction(
        auth: InteractiveChatAuth,
        draft: PredictionDraft,
    ): PredictionOverlay = mutationMutex.withLock {
        api.createPrediction(auth, draft).also(::ingestPrediction)
    }

    suspend fun endPrediction(
        auth: InteractiveChatAuth,
        predictionId: String,
        status: PredictionEndStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay = mutationMutex.withLock {
        api.endPrediction(
            auth = auth,
            predictionId = predictionId,
            status = status,
            winningOutcomeId = winningOutcomeId,
        ).also(::ingestPrediction)
    }

    fun ingest(event: InteractiveChatOverlayEvent) {
        mutableState.update { current -> InteractiveChatOverlayReducer.reduce(current, event) }
    }

    fun ingestPoll(poll: PollOverlay) {
        ingest(InteractiveChatOverlayEvent.PollSnapshot(poll))
    }

    fun ingestPrediction(prediction: PredictionOverlay) {
        ingest(InteractiveChatOverlayEvent.PredictionSnapshot(prediction))
    }

    fun clearChannel(channelId: String) {
        ingest(InteractiveChatOverlayEvent.ClearChannel(channelId))
    }

    override fun close() = api.close()
}
