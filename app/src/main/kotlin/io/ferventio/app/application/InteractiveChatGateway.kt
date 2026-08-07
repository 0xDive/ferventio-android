package io.ferventio.app.application

import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.twitch.PollEndStatus
import io.ferventio.app.twitch.PredictionEndStatus
import io.ferventio.app.twitch.TwitchInteractiveApiClient
import java.io.Closeable

internal interface InteractiveChatGateway : Closeable {
    suspend fun getPolls(auth: InteractiveChatAuth): List<PollOverlay>
    suspend fun createPoll(auth: InteractiveChatAuth, draft: PollDraft): PollOverlay
    suspend fun endPoll(auth: InteractiveChatAuth, pollId: String, status: PollEndStatus): PollOverlay
    suspend fun getPredictions(auth: InteractiveChatAuth): List<PredictionOverlay>
    suspend fun createPrediction(auth: InteractiveChatAuth, draft: PredictionDraft): PredictionOverlay
    suspend fun endPrediction(
        auth: InteractiveChatAuth,
        predictionId: String,
        status: PredictionEndStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay
}

internal class TwitchInteractiveChatGateway(
    private val client: TwitchInteractiveApiClient = TwitchInteractiveApiClient(),
) : InteractiveChatGateway {
    override suspend fun getPolls(auth: InteractiveChatAuth): List<PollOverlay> = client.getPolls(
        clientId = auth.clientId,
        token = auth.accessToken,
        broadcasterId = auth.broadcasterId,
    )

    override suspend fun createPoll(auth: InteractiveChatAuth, draft: PollDraft): PollOverlay =
        client.createPoll(
            clientId = auth.clientId,
            token = auth.accessToken,
            broadcasterId = auth.broadcasterId,
            draft = draft,
        )

    override suspend fun endPoll(
        auth: InteractiveChatAuth,
        pollId: String,
        status: PollEndStatus,
    ): PollOverlay = client.endPoll(
        clientId = auth.clientId,
        token = auth.accessToken,
        broadcasterId = auth.broadcasterId,
        pollId = pollId,
        status = status,
    )

    override suspend fun getPredictions(auth: InteractiveChatAuth): List<PredictionOverlay> =
        client.getPredictions(
            clientId = auth.clientId,
            token = auth.accessToken,
            broadcasterId = auth.broadcasterId,
        )

    override suspend fun createPrediction(
        auth: InteractiveChatAuth,
        draft: PredictionDraft,
    ): PredictionOverlay = client.createPrediction(
        clientId = auth.clientId,
        token = auth.accessToken,
        broadcasterId = auth.broadcasterId,
        draft = draft,
    )

    override suspend fun endPrediction(
        auth: InteractiveChatAuth,
        predictionId: String,
        status: PredictionEndStatus,
        winningOutcomeId: String?,
    ): PredictionOverlay = client.endPrediction(
        clientId = auth.clientId,
        token = auth.accessToken,
        broadcasterId = auth.broadcasterId,
        predictionId = predictionId,
        status = status,
        winningOutcomeId = winningOutcomeId,
    )

    override fun close() = client.close()
}
