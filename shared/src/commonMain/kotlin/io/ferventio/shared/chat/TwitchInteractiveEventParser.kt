package io.ferventio.shared.chat

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object TwitchInteractiveEventParser {
    fun parse(envelope: TwitchEventSubProtocolEnvelope): InteractiveChatOverlayEvent? {
        if (envelope.type != "notification") return null
        val subscriptionType = envelope.subscriptionType ?: return null
        if (subscriptionType !in SUPPORTED_TYPES) return null
        val event = envelope.eventPayload
            ?: throw IllegalArgumentException("$subscriptionType is missing its event payload")
        return parseEvent(subscriptionType, event, envelope.messageTimestamp.orEmpty())
    }

    fun parseEvent(
        subscriptionType: String,
        event: JsonObject,
        messageTimestamp: String,
    ): InteractiveChatOverlayEvent? = when (subscriptionType) {
        POLL_BEGIN, POLL_PROGRESS, POLL_END ->
            InteractiveChatOverlayEvent.PollSnapshot(
                parsePoll(subscriptionType, event, messageTimestamp),
            )
        PREDICTION_BEGIN, PREDICTION_PROGRESS, PREDICTION_LOCK, PREDICTION_END ->
            InteractiveChatOverlayEvent.PredictionSnapshot(
                parsePrediction(subscriptionType, event, messageTimestamp),
            )
        else -> null
    }

    private fun parsePoll(
        subscriptionType: String,
        event: JsonObject,
        messageTimestamp: String,
    ): PollOverlay {
        val id = event.requiredString("id")
        val channelId = event.requiredString("broadcaster_user_id")
        val startedAt = event.string("started_at")
        val channelPointsVoting = event.objectOrNull("channel_points_voting")
        val bitsVoting = event.objectOrNull("bits_voting")
        return PollOverlay(
            id = id,
            channelId = channelId,
            title = event.requiredString("title"),
            choices = event.arrayOrEmpty("choices").mapNotNull { element ->
                val choice = element as? JsonObject ?: return@mapNotNull null
                val choiceId = choice.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                PollChoice(
                    id = choiceId,
                    title = choice.string("title").orEmpty(),
                    votes = choice.int("votes") ?: 0,
                    channelPointsVotes = choice.int("channel_points_votes") ?: 0,
                    bitsVotes = choice.int("bits_votes") ?: 0,
                )
            },
            status = when (subscriptionType) {
                POLL_BEGIN, POLL_PROGRESS -> PollStatus.ACTIVE
                else -> pollStatus(event.string("status"))
            },
            startedAtMillis = timestampMillis(startedAt, messageTimestamp),
            endsAtMillis = event.string("ends_at")?.let { timestampMillis(it, messageTimestamp) },
            endedAtMillis = event.string("ended_at")?.let { timestampMillis(it, messageTimestamp) },
            channelPointsVotingEnabled = channelPointsVoting?.boolean("is_enabled") ?: false,
            channelPointsPerVote = channelPointsVoting?.int("amount_per_vote") ?: 0,
            bitsVotingEnabled = bitsVoting?.boolean("is_enabled") ?: false,
            bitsPerVote = bitsVoting?.int("amount_per_vote") ?: 0,
            updatedAtMillis = timestampMillis(messageTimestamp, startedAt),
        )
    }

    private fun parsePrediction(
        subscriptionType: String,
        event: JsonObject,
        messageTimestamp: String,
    ): PredictionOverlay {
        val startedAt = event.string("started_at")
        return PredictionOverlay(
            id = event.requiredString("id"),
            channelId = event.requiredString("broadcaster_user_id"),
            title = event.requiredString("title"),
            outcomes = event.arrayOrEmpty("outcomes").mapNotNull { element ->
                val outcome = element as? JsonObject ?: return@mapNotNull null
                val outcomeId = outcome.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                PredictionOutcome(
                    id = outcomeId,
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
            status = when (subscriptionType) {
                PREDICTION_BEGIN, PREDICTION_PROGRESS -> PredictionStatus.ACTIVE
                PREDICTION_LOCK -> PredictionStatus.LOCKED
                else -> predictionStatus(event.string("status"))
            },
            startedAtMillis = timestampMillis(startedAt, messageTimestamp),
            locksAtMillis = event.string("locks_at")?.let { timestampMillis(it, messageTimestamp) },
            lockedAtMillis = event.string("locked_at")?.let { timestampMillis(it, messageTimestamp) },
            endedAtMillis = event.string("ended_at")?.let { timestampMillis(it, messageTimestamp) },
            winningOutcomeId = event.string("winning_outcome_id")?.takeIf(String::isNotBlank),
            updatedAtMillis = timestampMillis(messageTimestamp, startedAt),
        )
    }

    private fun pollStatus(value: String?): PollStatus = when (value?.lowercase()) {
        "active" -> PollStatus.ACTIVE
        "completed" -> PollStatus.COMPLETED
        "terminated" -> PollStatus.TERMINATED
        "archived" -> PollStatus.ARCHIVED
        "moderated" -> PollStatus.MODERATED
        "invalid" -> PollStatus.INVALID
        else -> PollStatus.UNKNOWN
    }

    private fun predictionStatus(value: String?): PredictionStatus = when (value?.lowercase()) {
        "active" -> PredictionStatus.ACTIVE
        "locked" -> PredictionStatus.LOCKED
        "resolved" -> PredictionStatus.RESOLVED
        "canceled", "cancelled" -> PredictionStatus.CANCELED
        else -> PredictionStatus.UNKNOWN
    }

    private fun timestampMillis(primary: String?, fallback: String?): Long =
        sequenceOf(primary, fallback)
            .mapNotNull { value ->
                value?.trim()?.takeIf(String::isNotEmpty)?.let { timestamp ->
                    runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull()
                }
            }
            .firstOrNull()
            ?: 0L

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.takeIf(String::isNotBlank) ?: error("Event is missing $name")

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        this[name] as? JsonArray ?: JsonArray(emptyList())

    private const val POLL_BEGIN = "channel.poll.begin"
    private const val POLL_PROGRESS = "channel.poll.progress"
    private const val POLL_END = "channel.poll.end"
    private const val PREDICTION_BEGIN = "channel.prediction.begin"
    private const val PREDICTION_PROGRESS = "channel.prediction.progress"
    private const val PREDICTION_LOCK = "channel.prediction.lock"
    private const val PREDICTION_END = "channel.prediction.end"
    private val SUPPORTED_TYPES = setOf(
        POLL_BEGIN,
        POLL_PROGRESS,
        POLL_END,
        PREDICTION_BEGIN,
        PREDICTION_PROGRESS,
        PREDICTION_LOCK,
        PREDICTION_END,
    )
}
