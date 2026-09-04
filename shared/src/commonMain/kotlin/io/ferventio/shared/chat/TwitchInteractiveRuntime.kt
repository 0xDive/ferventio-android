package io.ferventio.shared.chat

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationKind
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.domain.InteractiveOverlayDraftValidator
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.domain.StoredAuthentication
import kotlinx.coroutines.CancellationException

data class InteractiveMutationFailure(
    val kind: InteractiveMutationFailureKind,
    val recovery: InteractiveMutationRecovery,
)

/** Applies Poll/Prediction Helix mutations to the same shared overlay state fed by EventSub. */
class TwitchInteractiveRuntime(
    private val chatState: ChatRuntimeStateHolder,
    private val gateway: TwitchInteractiveMutationGateway = TwitchInteractiveMutationClient(),
) {
    suspend fun createPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PollDraft,
    ): PollOverlay {
        requireBroadcasterSession(authentication, broadcasterId)
        val validation = InteractiveOverlayDraftValidator.validatePoll(draft)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        return executeMutation(
            channelId = broadcasterId,
            kind = InteractiveMutationKind.CREATE_POLL,
            action = { gateway.createPoll(authentication, broadcasterId, draft) },
            publish = { poll ->
                chatState.applyInteractive(InteractiveChatOverlayEvent.PollSnapshot(poll))
            },
        )
    }

    suspend fun endPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        pollId: String,
        status: PollStatus,
    ): PollOverlay {
        requireBroadcasterSession(authentication, broadcasterId)
        require(pollId.isNotBlank()) { "Poll id must not be blank" }
        require(status == PollStatus.TERMINATED || status == PollStatus.ARCHIVED) {
            "Poll can only be terminated or archived"
        }
        val kind = if (status == PollStatus.ARCHIVED) {
            InteractiveMutationKind.ARCHIVE_POLL
        } else {
            InteractiveMutationKind.END_POLL
        }
        return executeMutation(
            channelId = broadcasterId,
            kind = kind,
            action = { gateway.endPoll(authentication, broadcasterId, pollId, status) },
            publish = { poll ->
                chatState.applyInteractive(InteractiveChatOverlayEvent.PollSnapshot(poll))
            },
        )
    }

    suspend fun createPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PredictionDraft,
    ): PredictionOverlay {
        requireBroadcasterSession(authentication, broadcasterId)
        val validation = InteractiveOverlayDraftValidator.validatePrediction(draft)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        return executeMutation(
            channelId = broadcasterId,
            kind = InteractiveMutationKind.CREATE_PREDICTION,
            action = { gateway.createPrediction(authentication, broadcasterId, draft) },
            publish = { prediction ->
                chatState.applyInteractive(InteractiveChatOverlayEvent.PredictionSnapshot(prediction))
            },
        )
    }

    suspend fun endPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        predictionId: String,
        status: PredictionStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay {
        requireBroadcasterSession(authentication, broadcasterId)
        require(predictionId.isNotBlank()) { "Prediction id must not be blank" }
        val kind = when (status) {
            PredictionStatus.LOCKED -> InteractiveMutationKind.LOCK_PREDICTION
            PredictionStatus.CANCELED -> InteractiveMutationKind.CANCEL_PREDICTION
            PredictionStatus.RESOLVED -> {
                require(!winningOutcomeId.isNullOrBlank()) {
                    "Resolved prediction requires a winning outcome id"
                }
                InteractiveMutationKind.RESOLVE_PREDICTION
            }
            else -> throw IllegalArgumentException(
                "Prediction can only be locked, canceled, or resolved",
            )
        }
        return executeMutation(
            channelId = broadcasterId,
            kind = kind,
            action = {
                gateway.endPrediction(
                    authentication = authentication,
                    broadcasterId = broadcasterId,
                    predictionId = predictionId,
                    status = status,
                    winningOutcomeId = winningOutcomeId,
                )
            },
            publish = { prediction ->
                chatState.applyInteractive(InteractiveChatOverlayEvent.PredictionSnapshot(prediction))
            },
        )
    }

    private suspend fun <T> executeMutation(
        channelId: String,
        kind: InteractiveMutationKind,
        action: suspend () -> T,
        publish: (T) -> Unit,
    ): T {
        chatState.applyInteractive(
            InteractiveChatOverlayEvent.MutationStarted(channelId, kind),
        )
        try {
            val result = action()
            publish(result)
            chatState.applyInteractive(
                InteractiveChatOverlayEvent.MutationSucceeded(channelId, kind),
            )
            return result
        } catch (cancelled: CancellationException) {
            chatState.applyInteractive(
                InteractiveChatOverlayEvent.MutationSucceeded(channelId, kind),
            )
            throw cancelled
        } catch (error: Throwable) {
            val failure = classifyInteractiveMutationFailure(error)
            chatState.applyInteractive(
                InteractiveChatOverlayEvent.MutationFailed(
                    channelId = channelId,
                    kind = kind,
                    failureKind = failure.kind,
                    recovery = failure.recovery,
                ),
            )
            val apiError = error.findCause<TwitchInteractiveMutationException>()
            if (apiError?.statusCode == 401) {
                chatState.markAuthenticationRequired(apiError.message)
            }
            throw error
        }
    }

    private fun requireBroadcasterSession(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ) {
        val normalizedBroadcasterId = broadcasterId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Broadcaster id must not be blank")
        val session = authentication.accessLease?.session
            ?: throw IllegalArgumentException("Twitch access lease is unavailable")
        require(session.userId == normalizedBroadcasterId) {
            "Polls and Predictions can only be managed for the authenticated broadcaster"
        }
    }
}

internal fun classifyInteractiveMutationFailure(error: Throwable): InteractiveMutationFailure {
    val scopeError = error.findCause<TwitchInteractiveScopeException>()
    if (scopeError != null) {
        return InteractiveMutationFailure(
            kind = InteractiveMutationFailureKind.PERMISSION,
            recovery = InteractiveMutationRecovery.NONE,
        )
    }
    val apiError = error.findCause<TwitchInteractiveMutationException>()
    if (apiError != null) {
        return when (apiError.statusCode) {
            401 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.AUTHENTICATION,
                InteractiveMutationRecovery.RETRY,
            )
            403 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.PERMISSION,
                InteractiveMutationRecovery.NONE,
            )
            408 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.NETWORK,
                InteractiveMutationRecovery.REFRESH,
            )
            429 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.RATE_LIMITED,
                InteractiveMutationRecovery.RETRY,
            )
            400, 404, 409, 410, 422 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.CONFLICT,
                InteractiveMutationRecovery.NONE,
            )
            in 500..599 -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.SERVER,
                InteractiveMutationRecovery.REFRESH,
            )
            else -> InteractiveMutationFailure(
                InteractiveMutationFailureKind.UNKNOWN,
                InteractiveMutationRecovery.NONE,
            )
        }
    }
    return InteractiveMutationFailure(
        InteractiveMutationFailureKind.UNKNOWN,
        InteractiveMutationRecovery.NONE,
    )
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
