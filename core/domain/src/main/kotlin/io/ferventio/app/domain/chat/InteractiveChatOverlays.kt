package io.ferventio.app.domain

enum class PollStatus {
    ACTIVE,
    COMPLETED,
    TERMINATED,
    ARCHIVED,
    MODERATED,
    INVALID,
    UNKNOWN,
}

data class PollChoice(
    val id: String,
    val title: String,
    val votes: Int = 0,
    val channelPointsVotes: Int = 0,
    val bitsVotes: Int = 0,
)

data class PollOverlay(
    val id: String,
    val channelId: String,
    val title: String,
    val choices: List<PollChoice>,
    val status: PollStatus,
    val startedAtMillis: Long,
    val endsAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val channelPointsVotingEnabled: Boolean = false,
    val channelPointsPerVote: Int = 0,
    val bitsVotingEnabled: Boolean = false,
    val bitsPerVote: Int = 0,
    val updatedAtMillis: Long,
) {
    val totalVotes: Int get() = choices.sumOf(PollChoice::votes)
    val isActive: Boolean get() = status == PollStatus.ACTIVE

    fun voteShare(choiceId: String): Double {
        val total = totalVotes
        if (total <= 0) return 0.0
        val votes = choices.firstOrNull { it.id == choiceId }?.votes ?: return 0.0
        return votes.toDouble() / total.toDouble()
    }
}

enum class PredictionStatus {
    ACTIVE,
    LOCKED,
    RESOLVED,
    CANCELED,
    UNKNOWN,
}

enum class PredictionOutcomeColor {
    BLUE,
    PINK,
    UNKNOWN,
}

data class PredictionOutcome(
    val id: String,
    val title: String,
    val users: Int = 0,
    val channelPoints: Long = 0L,
    val color: PredictionOutcomeColor = PredictionOutcomeColor.UNKNOWN,
)

data class PredictionOverlay(
    val id: String,
    val channelId: String,
    val title: String,
    val outcomes: List<PredictionOutcome>,
    val status: PredictionStatus,
    val startedAtMillis: Long,
    val locksAtMillis: Long? = null,
    val lockedAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val winningOutcomeId: String? = null,
    val updatedAtMillis: Long,
) {
    val totalUsers: Int get() = outcomes.sumOf(PredictionOutcome::users)
    val totalChannelPoints: Long get() = outcomes.sumOf(PredictionOutcome::channelPoints)
    val isActive: Boolean get() = status == PredictionStatus.ACTIVE
    val isLocked: Boolean get() = status == PredictionStatus.LOCKED

    fun pointsShare(outcomeId: String): Double {
        val total = totalChannelPoints
        if (total <= 0L) return 0.0
        val points = outcomes.firstOrNull { it.id == outcomeId }?.channelPoints ?: return 0.0
        return points.toDouble() / total.toDouble()
    }
}

enum class InteractiveMutationKind {
    CREATE_POLL,
    END_POLL,
    ARCHIVE_POLL,
    CREATE_PREDICTION,
    LOCK_PREDICTION,
    CANCEL_PREDICTION,
    RESOLVE_PREDICTION,
}

enum class InteractiveMutationFailureKind {
    AUTHENTICATION,
    PERMISSION,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    CONFLICT,
    UNKNOWN,
}

enum class InteractiveMutationRecovery {
    NONE,
    RETRY,
    REFRESH,
}

data class InteractiveMutationStatus(
    val kind: InteractiveMutationKind,
    val inFlight: Boolean = true,
    val failed: Boolean = false,
    val failureKind: InteractiveMutationFailureKind? = null,
    val recovery: InteractiveMutationRecovery = InteractiveMutationRecovery.NONE,
)

data class InteractiveChatOverlayState(
    val pollsByChannel: Map<String, PollOverlay> = emptyMap(),
    val predictionsByChannel: Map<String, PredictionOverlay> = emptyMap(),
    val mutationsByChannel: Map<String, InteractiveMutationStatus> = emptyMap(),
)

sealed interface InteractiveChatOverlayEvent {
    data class PollSnapshot(val poll: PollOverlay) : InteractiveChatOverlayEvent
    data class PredictionSnapshot(val prediction: PredictionOverlay) : InteractiveChatOverlayEvent
    data class MutationStarted(
        val channelId: String,
        val kind: InteractiveMutationKind,
    ) : InteractiveChatOverlayEvent
    data class MutationSucceeded(
        val channelId: String,
        val kind: InteractiveMutationKind,
    ) : InteractiveChatOverlayEvent
    data class MutationFailed(
        val channelId: String,
        val kind: InteractiveMutationKind,
        val failureKind: InteractiveMutationFailureKind,
        val recovery: InteractiveMutationRecovery,
    ) : InteractiveChatOverlayEvent
    data class ClearChannel(val channelId: String) : InteractiveChatOverlayEvent
}

/**
 * Snapshot reducer shared by initial Helix hydration and EventSub updates.
 * Stale events are ignored so delayed progress notifications cannot roll an
 * overlay backwards after a lock/end snapshot.
 */
object InteractiveChatOverlayReducer {
    fun reduce(
        state: InteractiveChatOverlayState,
        event: InteractiveChatOverlayEvent,
    ): InteractiveChatOverlayState = when (event) {
        is InteractiveChatOverlayEvent.PollSnapshot -> {
            val incoming = event.poll
            val existing = state.pollsByChannel[incoming.channelId]
            if (existing != null && incoming.updatedAtMillis < existing.updatedAtMillis) state
            else state.copy(pollsByChannel = state.pollsByChannel + (incoming.channelId to incoming))
        }

        is InteractiveChatOverlayEvent.PredictionSnapshot -> {
            val incoming = event.prediction
            val existing = state.predictionsByChannel[incoming.channelId]
            if (existing != null && incoming.updatedAtMillis < existing.updatedAtMillis) state
            else state.copy(
                predictionsByChannel = state.predictionsByChannel + (incoming.channelId to incoming),
            )
        }

        is InteractiveChatOverlayEvent.MutationStarted -> state.copy(
            mutationsByChannel = state.mutationsByChannel + (
                event.channelId to InteractiveMutationStatus(kind = event.kind)
            ),
        )

        is InteractiveChatOverlayEvent.MutationSucceeded -> {
            val current = state.mutationsByChannel[event.channelId]
            if (current?.kind != event.kind) state
            else state.copy(mutationsByChannel = state.mutationsByChannel - event.channelId)
        }

        is InteractiveChatOverlayEvent.MutationFailed -> {
            val current = state.mutationsByChannel[event.channelId]
            if (current?.kind != event.kind) state
            else state.copy(
                mutationsByChannel = state.mutationsByChannel + (
                    event.channelId to current.copy(
                        inFlight = false,
                        failed = true,
                        failureKind = event.failureKind,
                        recovery = event.recovery,
                    )
                ),
            )
        }

        is InteractiveChatOverlayEvent.ClearChannel -> state.copy(
            pollsByChannel = state.pollsByChannel - event.channelId,
            predictionsByChannel = state.predictionsByChannel - event.channelId,
            mutationsByChannel = state.mutationsByChannel - event.channelId,
        )
    }
}

data class PollDraft(
    val title: String,
    val choices: List<String>,
    val durationSeconds: Int,
    val channelPointsVotingEnabled: Boolean = false,
    val channelPointsPerVote: Int = 0,
)

data class PredictionDraft(
    val title: String,
    val outcomes: List<String>,
    val predictionWindowSeconds: Int,
)

object InteractiveOverlayDraftValidator {
    fun validatePoll(draft: PollDraft): List<String> = buildList {
        if (draft.title.isBlank() || draft.title.length > 60) {
            add("Poll title must contain 1 to 60 characters")
        }
        if (draft.choices.size !in 2..5) add("Poll must contain 2 to 5 choices")
        if (draft.choices.any { it.isBlank() || it.length > 25 }) {
            add("Each poll choice must contain 1 to 25 characters")
        }
        if (draft.choices.map { it.trim().lowercase() }.distinct().size != draft.choices.size) {
            add("Poll choices must be unique")
        }
        if (draft.durationSeconds !in 15..1_800) {
            add("Poll duration must be between 15 and 1800 seconds")
        }
        if (draft.channelPointsVotingEnabled && draft.channelPointsPerVote !in 1..1_000_000) {
            add("Channel Points per vote must be between 1 and 1000000")
        }
    }

    fun validatePrediction(draft: PredictionDraft): List<String> = buildList {
        if (draft.title.isBlank() || draft.title.length > 45) {
            add("Prediction title must contain 1 to 45 characters")
        }
        if (draft.outcomes.size !in 2..10) add("Prediction must contain 2 to 10 outcomes")
        if (draft.outcomes.any { it.isBlank() || it.length > 25 }) {
            add("Each prediction outcome must contain 1 to 25 characters")
        }
        if (draft.outcomes.map { it.trim().lowercase() }.distinct().size != draft.outcomes.size) {
            add("Prediction outcomes must be unique")
        }
        if (draft.predictionWindowSeconds !in 30..1_800) {
            add("Prediction window must be between 30 and 1800 seconds")
        }
    }
}
