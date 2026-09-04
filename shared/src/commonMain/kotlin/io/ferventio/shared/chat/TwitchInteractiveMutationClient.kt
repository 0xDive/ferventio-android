package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.InteractiveOverlayDraftValidator
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

interface TwitchInteractiveMutationGateway {
    suspend fun createPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PollDraft,
    ): PollOverlay

    suspend fun endPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        pollId: String,
        status: PollStatus,
    ): PollOverlay

    suspend fun createPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PredictionDraft,
    ): PredictionOverlay

    suspend fun endPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        predictionId: String,
        status: PredictionStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay
}

class TwitchInteractiveMutationException(
    val operation: String,
    val statusCode: Int,
    val twitchMessage: String,
) : IllegalStateException("$operation failed ($statusCode): $twitchMessage")

class TwitchInteractiveScopeException(
    val requiredScope: String,
) : IllegalStateException("Twitch scope required: $requiredScope")

/** KMP Helix transport for broadcaster-owned Poll and Prediction mutations. */
class TwitchInteractiveMutationClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) : TwitchInteractiveMutationGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PollDraft,
    ): PollOverlay {
        val auth = requireBroadcaster(authentication, broadcasterId, POLL_SCOPE)
        val validation = InteractiveOverlayDraftValidator.validatePoll(draft)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        val payload = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "broadcaster_id" to JsonPrimitive(auth.broadcasterId),
            "title" to JsonPrimitive(draft.title.trim()),
            "choices" to JsonArray(
                draft.choices.map { choice ->
                    JsonObject(mapOf("title" to JsonPrimitive(choice.trim())))
                },
            ),
            "duration" to JsonPrimitive(draft.durationSeconds),
        )
        if (draft.channelPointsVotingEnabled) {
            payload["channel_points_voting_enabled"] = JsonPrimitive(true)
            payload["channel_points_per_vote"] = JsonPrimitive(draft.channelPointsPerVote)
        }
        val response = client.post(POLLS_URL) {
            helixHeaders(auth)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(payload).toString())
        }
        return parsePoll(requireSuccess("Create poll", response))
    }

    override suspend fun endPoll(
        authentication: StoredAuthentication,
        broadcasterId: String,
        pollId: String,
        status: PollStatus,
    ): PollOverlay {
        require(status == PollStatus.TERMINATED || status == PollStatus.ARCHIVED) {
            "Poll can only be terminated or archived"
        }
        val normalizedPollId = pollId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Poll id must not be blank")
        val auth = requireBroadcaster(authentication, broadcasterId, POLL_SCOPE)
        val response = client.put(POLLS_URL) {
            helixHeaders(auth)
            contentType(ContentType.Application.Json)
            setBody(
                JsonObject(
                    mapOf(
                        "broadcaster_id" to JsonPrimitive(auth.broadcasterId),
                        "id" to JsonPrimitive(normalizedPollId),
                        "status" to JsonPrimitive(status.name.lowercase()),
                    ),
                ).toString(),
            )
        }
        return parsePoll(requireSuccess("End poll", response))
    }

    override suspend fun createPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        draft: PredictionDraft,
    ): PredictionOverlay {
        val auth = requireBroadcaster(authentication, broadcasterId, PREDICTION_SCOPE)
        val validation = InteractiveOverlayDraftValidator.validatePrediction(draft)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        val response = client.post(PREDICTIONS_URL) {
            helixHeaders(auth)
            contentType(ContentType.Application.Json)
            setBody(
                JsonObject(
                    mapOf(
                        "broadcaster_id" to JsonPrimitive(auth.broadcasterId),
                        "title" to JsonPrimitive(draft.title.trim()),
                        "outcomes" to JsonArray(
                            draft.outcomes.map { outcome ->
                                JsonObject(mapOf("title" to JsonPrimitive(outcome.trim())))
                            },
                        ),
                        "prediction_window" to JsonPrimitive(draft.predictionWindowSeconds),
                    ),
                ).toString(),
            )
        }
        return parsePrediction(requireSuccess("Create prediction", response))
    }

    override suspend fun endPrediction(
        authentication: StoredAuthentication,
        broadcasterId: String,
        predictionId: String,
        status: PredictionStatus,
        winningOutcomeId: String?,
    ): PredictionOverlay {
        require(
            status == PredictionStatus.LOCKED ||
                status == PredictionStatus.CANCELED ||
                status == PredictionStatus.RESOLVED,
        ) { "Prediction can only be locked, canceled, or resolved" }
        val normalizedPredictionId = predictionId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Prediction id must not be blank")
        val normalizedWinner = winningOutcomeId?.trim()?.takeIf(String::isNotEmpty)
        require(status != PredictionStatus.RESOLVED || normalizedWinner != null) {
            "Resolved prediction requires a winning outcome id"
        }
        val auth = requireBroadcaster(authentication, broadcasterId, PREDICTION_SCOPE)
        val payload = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "broadcaster_id" to JsonPrimitive(auth.broadcasterId),
            "id" to JsonPrimitive(normalizedPredictionId),
            "status" to JsonPrimitive(status.name.lowercase()),
        )
        if (status == PredictionStatus.RESOLVED) {
            payload["winning_outcome_id"] = JsonPrimitive(requireNotNull(normalizedWinner))
        }
        val response = client.put(PREDICTIONS_URL) {
            helixHeaders(auth)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(payload).toString())
        }
        return parsePrediction(requireSuccess("End prediction", response))
    }

    private suspend fun requireSuccess(operation: String, response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.value in 200..299) return body
        val twitchMessage = runCatching {
            val root = json.parseToJsonElement(body) as? JsonObject
            root?.string("message") ?: root?.string("error")
        }.getOrNull().orEmpty().ifBlank { body.take(300).ifBlank { "Twitch request failed" } }
        throw TwitchInteractiveMutationException(
            operation = operation,
            statusCode = response.status.value,
            twitchMessage = twitchMessage,
        )
    }

    private fun requireBroadcaster(
        authentication: StoredAuthentication,
        broadcasterId: String,
        requiredScope: String,
    ): HelixAuthentication {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease)
        val normalizedBroadcasterId = broadcasterId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Broadcaster id must not be blank")
        require(lease.session.userId == normalizedBroadcasterId) {
            "Polls and Predictions can only be managed for the authenticated broadcaster"
        }
        if (requiredScope !in lease.session.scopes) {
            throw TwitchInteractiveScopeException(requiredScope)
        }
        return HelixAuthentication(
            accessToken = lease.accessToken,
            clientId = lease.session.clientId,
            broadcasterId = normalizedBroadcasterId,
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.helixHeaders(auth: HelixAuthentication) {
        header(HttpHeaders.Authorization, "Bearer ${auth.accessToken}")
        header("Client-Id", auth.clientId)
    }

    private fun parsePoll(body: String): PollOverlay {
        val item = firstDataObject(body, "poll")
        val channelId = item.string("broadcaster_id").orEmpty()
        val choices = item.array("choices").mapNotNull { element ->
            val choice = element as? JsonObject ?: return@mapNotNull null
            PollChoice(
                id = choice.string("id").orEmpty(),
                title = choice.string("title").orEmpty(),
                votes = choice.int("votes") ?: 0,
                channelPointsVotes = choice.int("channel_points_votes") ?: 0,
                bitsVotes = choice.int("bits_votes") ?: 0,
            )
        }
        val startedAt = item.string("started_at").toEpochMillis()
        return PollOverlay(
            id = item.string("id").orEmpty(),
            channelId = channelId,
            title = item.string("title").orEmpty(),
            choices = choices,
            status = parsePollStatus(item.string("status")),
            startedAtMillis = startedAt,
            endsAtMillis = item.string("ends_at").toEpochMillisOrNull(),
            endedAtMillis = item.string("ended_at").toEpochMillisOrNull(),
            channelPointsVotingEnabled = item.boolean("channel_points_voting_enabled") ?: false,
            channelPointsPerVote = item.int("channel_points_per_vote") ?: 0,
            bitsVotingEnabled = item.boolean("bits_voting_enabled") ?: false,
            bitsPerVote = item.int("bits_per_vote") ?: 0,
            updatedAtMillis = maxOf(
                startedAt,
                item.string("ended_at").toEpochMillisOrNull() ?: Long.MIN_VALUE,
            ),
        )
    }

    private fun parsePrediction(body: String): PredictionOverlay {
        val item = firstDataObject(body, "prediction")
        val startedAt = item.string("created_at").toEpochMillis()
        val outcomes = item.array("outcomes").mapNotNull { element ->
            val outcome = element as? JsonObject ?: return@mapNotNull null
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
        }
        val lockedAt = item.string("locked_at").toEpochMillisOrNull()
        val endedAt = item.string("ended_at").toEpochMillisOrNull()
        return PredictionOverlay(
            id = item.string("id").orEmpty(),
            channelId = item.string("broadcaster_id").orEmpty(),
            title = item.string("title").orEmpty(),
            outcomes = outcomes,
            status = parsePredictionStatus(item.string("status")),
            startedAtMillis = startedAt,
            locksAtMillis = item.string("locks_at").toEpochMillisOrNull(),
            lockedAtMillis = lockedAt,
            endedAtMillis = endedAt,
            winningOutcomeId = item.string("winning_outcome_id"),
            updatedAtMillis = maxOf(startedAt, lockedAt ?: Long.MIN_VALUE, endedAt ?: Long.MIN_VALUE),
        )
    }

    private fun firstDataObject(body: String, resource: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("Twitch returned malformed $resource JSON")
        return (root["data"] as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: throw IllegalStateException("Twitch returned no $resource data")
    }

    private fun parsePollStatus(value: String?): PollStatus = when (value?.lowercase()) {
        "active" -> PollStatus.ACTIVE
        "completed" -> PollStatus.COMPLETED
        "terminated" -> PollStatus.TERMINATED
        "archived" -> PollStatus.ARCHIVED
        "moderated" -> PollStatus.MODERATED
        "invalid" -> PollStatus.INVALID
        else -> PollStatus.UNKNOWN
    }

    private fun parsePredictionStatus(value: String?): PredictionStatus = when (value?.lowercase()) {
        "active" -> PredictionStatus.ACTIVE
        "locked" -> PredictionStatus.LOCKED
        "resolved" -> PredictionStatus.RESOLVED
        "canceled", "cancelled" -> PredictionStatus.CANCELED
        else -> PredictionStatus.UNKNOWN
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun String?.toEpochMillis(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    private data class HelixAuthentication(
        val accessToken: String,
        val clientId: String,
        val broadcasterId: String,
    )

    private companion object {
        const val POLLS_URL = "https://api.twitch.tv/helix/polls"
        const val PREDICTIONS_URL = "https://api.twitch.tv/helix/predictions"
        const val POLL_SCOPE = "channel:manage:polls"
        const val PREDICTION_SCOPE = "channel:manage:predictions"
    }
}
