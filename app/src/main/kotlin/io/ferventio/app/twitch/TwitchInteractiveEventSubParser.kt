package io.ferventio.app.twitch

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Maps Twitch EventSub Poll/Prediction payloads into the same snapshot model used
 * by Helix hydration. The coordinator can therefore reduce both sources through
 * one state contract.
 */
object TwitchInteractiveEventSubParser {
    fun parse(
        subscriptionType: String,
        event: JsonObject,
        observedAtMillis: Long,
    ): InteractiveChatOverlayEvent? = when (subscriptionType) {
        POLL_BEGIN, POLL_PROGRESS, POLL_END ->
            InteractiveChatOverlayEvent.PollSnapshot(
                parsePoll(subscriptionType, event, observedAtMillis),
            )

        PREDICTION_BEGIN, PREDICTION_PROGRESS, PREDICTION_LOCK, PREDICTION_END ->
            InteractiveChatOverlayEvent.PredictionSnapshot(
                parsePrediction(subscriptionType, event, observedAtMillis),
            )

        else -> null
    }

    private fun parsePoll(
        type: String,
        event: JsonObject,
        observedAtMillis: Long,
    ): PollOverlay {
        val channelPointsVoting = event.obj("channel_points_voting")
        val bitsVoting = event.obj("bits_voting")
        return PollOverlay(
            id = event.string("id").orEmpty(),
            channelId = event.string("broadcaster_user_id").orEmpty(),
            title = event.string("title").orEmpty(),
            choices = event.array("choices").map { choiceElement ->
                val choice = choiceElement.jsonObject
                PollChoice(
                    id = choice.string("id").orEmpty(),
                    title = choice.string("title").orEmpty(),
                    votes = choice.int("votes") ?: 0,
                    channelPointsVotes = choice.int("channel_points_votes") ?: 0,
                    bitsVotes = choice.int("bits_votes") ?: 0,
                )
            },
            status = when (type) {
                POLL_BEGIN, POLL_PROGRESS -> PollStatus.ACTIVE
                POLL_END -> when (event.string("status")?.lowercase()) {
                    "completed" -> PollStatus.COMPLETED
                    "archived" -> PollStatus.ARCHIVED
                    "terminated" -> PollStatus.TERMINATED
                    "moderated" -> PollStatus.MODERATED
                    "invalid" -> PollStatus.INVALID
                    else -> PollStatus.UNKNOWN
                }
                else -> PollStatus.UNKNOWN
            },
            startedAtMillis = event.instantMillis("started_at") ?: 0L,
            endsAtMillis = event.instantMillis("ends_at"),
            endedAtMillis = event.instantMillis("ended_at"),
            channelPointsVotingEnabled = channelPointsVoting?.boolean("is_enabled") ?: false,
            channelPointsPerVote = channelPointsVoting?.int("amount_per_vote") ?: 0,
            // Twitch still includes this legacy object in EventSub payloads, but
            // Bits voting is no longer supported. Preserve the wire value only.
            bitsVotingEnabled = bitsVoting?.boolean("is_enabled") ?: false,
            bitsPerVote = bitsVoting?.int("amount_per_vote") ?: 0,
            updatedAtMillis = observedAtMillis,
        )
    }

    private fun parsePrediction(
        type: String,
        event: JsonObject,
        observedAtMillis: Long,
    ): PredictionOverlay = PredictionOverlay(
        id = event.string("id").orEmpty(),
        channelId = event.string("broadcaster_user_id").orEmpty(),
        title = event.string("title").orEmpty(),
        outcomes = event.array("outcomes").map { outcomeElement ->
            val outcome = outcomeElement.jsonObject
            PredictionOutcome(
                id = outcome.string("id").orEmpty(),
                title = outcome.string("title").orEmpty(),
                users = outcome.int("users") ?: 0,
                channelPoints = outcome.long("channel_points") ?: 0L,
                color = when (outcome.string("color")?.lowercase()) {
                    "blue" -> PredictionOutcomeColor.BLUE
                    "pink" -> PredictionOutcomeColor.PINK
                    else -> PredictionOutcomeColor.UNKNOWN
                },
            )
        },
        status = when (type) {
            PREDICTION_BEGIN, PREDICTION_PROGRESS -> PredictionStatus.ACTIVE
            PREDICTION_LOCK -> PredictionStatus.LOCKED
            PREDICTION_END -> when (event.string("status")?.lowercase()) {
                "resolved" -> PredictionStatus.RESOLVED
                "canceled" -> PredictionStatus.CANCELED
                else -> PredictionStatus.UNKNOWN
            }
            else -> PredictionStatus.UNKNOWN
        },
        startedAtMillis = event.instantMillis("started_at") ?: 0L,
        locksAtMillis = event.instantMillis("locks_at"),
        lockedAtMillis = event.instantMillis("locked_at"),
        endedAtMillis = event.instantMillis("ended_at"),
        winningOutcomeId = event.string("winning_outcome_id"),
        updatedAtMillis = observedAtMillis,
    )

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull?.takeUnless { it == "null" }

    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.array(key: String): JsonArray = get(key)?.let { element ->
        runCatching { element.jsonArray }.getOrNull()
    } ?: JsonArray(emptyList())

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.let { element ->
        runCatching { element.jsonObject }.getOrNull()
    }

    private fun JsonObject.instantMillis(key: String): Long? = string(key)?.let { raw ->
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    const val POLL_BEGIN = "channel.poll.begin"
    const val POLL_PROGRESS = "channel.poll.progress"
    const val POLL_END = "channel.poll.end"
    const val PREDICTION_BEGIN = "channel.prediction.begin"
    const val PREDICTION_PROGRESS = "channel.prediction.progress"
    const val PREDICTION_LOCK = "channel.prediction.lock"
    const val PREDICTION_END = "channel.prediction.end"
}
