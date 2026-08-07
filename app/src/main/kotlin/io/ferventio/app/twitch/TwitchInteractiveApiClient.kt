package io.ferventio.app.twitch

import io.ferventio.app.domain.InteractiveOverlayDraftValidator
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOutcome
import io.ferventio.app.domain.PredictionOutcomeColor
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.io.Closeable
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Twitch Helix adapter for Polls and Channel Points Predictions. */
class TwitchInteractiveApiClient(
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun getPolls(
        clientId: String,
        token: String,
        broadcasterId: String,
        first: Int = 20,
    ): List<PollOverlay> {
        val response = client.get(POLLS_URL) {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId.requireId("broadcaster"))
            parameter("first", first.coerceIn(1, 20))
        }
        return TwitchInteractiveApiParser.parsePollResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        )
    }

    suspend fun createPoll(
        clientId: String,
        token: String,
        broadcasterId: String,
        draft: PollDraft,
    ): PollOverlay {
        val validationErrors = InteractiveOverlayDraftValidator.validatePoll(draft)
        require(validationErrors.isEmpty()) { validationErrors.joinToString("; ") }

        val response = client.post(POLLS_URL) {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("broadcaster_id", JsonPrimitive(broadcasterId.requireId("broadcaster")))
                    put("title", JsonPrimitive(draft.title.trim()))
                    put(
                        "choices",
                        buildJsonArray {
                            draft.choices.forEach { choice ->
                                add(buildJsonObject { put("title", JsonPrimitive(choice.trim())) })
                            }
                        },
                    )
                    put("duration", JsonPrimitive(draft.durationSeconds))
                    if (draft.channelPointsVotingEnabled) {
                        put("channel_points_voting_enabled", JsonPrimitive(true))
                        put("channel_points_per_vote", JsonPrimitive(draft.channelPointsPerVote))
                    }
                }.toString(),
            )
        }
        return TwitchInteractiveApiParser.parsePollResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        ).singleOrNull() ?: error("Twitch Create Poll returned no poll")
    }

    suspend fun endPoll(
        clientId: String,
        token: String,
        broadcasterId: String,
        pollId: String,
        status: PollEndStatus,
    ): PollOverlay {
        val response = client.patch(POLLS_URL) {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("broadcaster_id", JsonPrimitive(broadcasterId.requireId("broadcaster")))
                    put("id", JsonPrimitive(pollId.requireId("poll")))
                    put("status", JsonPrimitive(status.name))
                }.toString(),
            )
        }
        return TwitchInteractiveApiParser.parsePollResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        ).singleOrNull() ?: error("Twitch End Poll returned no poll")
    }

    suspend fun getPredictions(
        clientId: String,
        token: String,
        broadcasterId: String,
        first: Int = 20,
    ): List<PredictionOverlay> {
        val response = client.get(PREDICTIONS_URL) {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId.requireId("broadcaster"))
            parameter("first", first.coerceIn(1, 25))
        }
        return TwitchInteractiveApiParser.parsePredictionResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        )
    }

    suspend fun createPrediction(
        clientId: String,
        token: String,
        broadcasterId: String,
        draft: PredictionDraft,
    ): PredictionOverlay {
        val validationErrors = InteractiveOverlayDraftValidator.validatePrediction(draft)
        require(validationErrors.isEmpty()) { validationErrors.joinToString("; ") }

        val response = client.post(PREDICTIONS_URL) {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("broadcaster_id", JsonPrimitive(broadcasterId.requireId("broadcaster")))
                    put("title", JsonPrimitive(draft.title.trim()))
                    put(
                        "outcomes",
                        buildJsonArray {
                            draft.outcomes.forEach { outcome ->
                                add(buildJsonObject { put("title", JsonPrimitive(outcome.trim())) })
                            }
                        },
                    )
                    put("prediction_window", JsonPrimitive(draft.predictionWindowSeconds))
                }.toString(),
            )
        }
        return TwitchInteractiveApiParser.parsePredictionResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        ).singleOrNull() ?: error("Twitch Create Prediction returned no prediction")
    }

    suspend fun endPrediction(
        clientId: String,
        token: String,
        broadcasterId: String,
        predictionId: String,
        status: PredictionEndStatus,
        winningOutcomeId: String? = null,
    ): PredictionOverlay {
        require(status != PredictionEndStatus.RESOLVED || !winningOutcomeId.isNullOrBlank()) {
            "Resolving a prediction requires a winning outcome"
        }

        val response = client.patch(PREDICTIONS_URL) {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("broadcaster_id", JsonPrimitive(broadcasterId.requireId("broadcaster")))
                    put("id", JsonPrimitive(predictionId.requireId("prediction")))
                    put("status", JsonPrimitive(status.name))
                    winningOutcomeId
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("winning_outcome_id", JsonPrimitive(it)) }
                }.toString(),
            )
        }
        return TwitchInteractiveApiParser.parsePredictionResponse(
            body = response.requireSuccessBody(),
            updatedAtMillis = clockMillis(),
        ).singleOrNull() ?: error("Twitch End Prediction returned no prediction")
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.twitchHeaders(clientId: String, token: String) {
        require(clientId.isNotBlank()) { "Twitch client id is empty" }
        require(token.isNotBlank()) { "Twitch access token is empty" }
        header("Client-Id", clientId)
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private suspend fun HttpResponse.requireSuccessBody(): String {
        val body = bodyAsText()
        if (status.value in 200..299) return body
        val twitchMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        error(twitchMessage ?: "Twitch request failed with HTTP ${status.value}")
    }

    private fun String.requireId(label: String): String = trim().also {
        require(it.isNotBlank()) { "Twitch $label id is empty" }
    }

    companion object {
        private const val POLLS_URL = "https://api.twitch.tv/helix/polls"
        private const val PREDICTIONS_URL = "https://api.twitch.tv/helix/predictions"
    }
}

enum class PollEndStatus {
    TERMINATED,
    ARCHIVED,
}

enum class PredictionEndStatus {
    LOCKED,
    CANCELED,
    RESOLVED,
}

/** Pure JSON parser shared by Helix hydration and parser tests. */
object TwitchInteractiveApiParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePollResponse(body: String, updatedAtMillis: Long): List<PollOverlay> {
        val data = responseData(body)
        return data.map { element -> parsePoll(element.jsonObject, updatedAtMillis) }
    }

    fun parsePredictionResponse(body: String, updatedAtMillis: Long): List<PredictionOverlay> {
        val data = responseData(body)
        return data.map { element -> parsePrediction(element.jsonObject, updatedAtMillis) }
    }

    private fun parsePoll(item: JsonObject, updatedAtMillis: Long): PollOverlay {
        val startedAt = item.instantMillis("started_at") ?: 0L
        val durationSeconds = item.int("duration") ?: 0
        return PollOverlay(
            id = item.string("id").orEmpty(),
            channelId = item.string("broadcaster_id").orEmpty(),
            title = item.string("title").orEmpty(),
            choices = item.array("choices").map { choiceElement ->
                val choice = choiceElement.jsonObject
                PollChoice(
                    id = choice.string("id").orEmpty(),
                    title = choice.string("title").orEmpty(),
                    votes = choice.int("votes") ?: 0,
                    channelPointsVotes = choice.int("channel_points_votes") ?: 0,
                    bitsVotes = choice.int("bits_votes") ?: 0,
                )
            },
            status = when (item.string("status")?.uppercase()) {
                "ACTIVE" -> PollStatus.ACTIVE
                "COMPLETED" -> PollStatus.COMPLETED
                "TERMINATED" -> PollStatus.TERMINATED
                "ARCHIVED" -> PollStatus.ARCHIVED
                "MODERATED" -> PollStatus.MODERATED
                "INVALID" -> PollStatus.INVALID
                else -> PollStatus.UNKNOWN
            },
            startedAtMillis = startedAt,
            endsAtMillis = startedAt
                .takeIf { it > 0L && durationSeconds > 0 }
                ?.plus(durationSeconds * 1_000L),
            endedAtMillis = item.instantMillis("ended_at"),
            channelPointsVotingEnabled = item.boolean("channel_points_voting_enabled") ?: false,
            channelPointsPerVote = item.int("channel_points_per_vote") ?: 0,
            bitsVotingEnabled = item.boolean("bits_voting_enabled") ?: false,
            bitsPerVote = item.int("bits_per_vote") ?: 0,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun parsePrediction(item: JsonObject, updatedAtMillis: Long): PredictionOverlay {
        val startedAt = item.instantMillis("created_at") ?: 0L
        val predictionWindowSeconds = item.int("prediction_window") ?: 0
        return PredictionOverlay(
            id = item.string("id").orEmpty(),
            channelId = item.string("broadcaster_id").orEmpty(),
            title = item.string("title").orEmpty(),
            outcomes = item.array("outcomes").map { outcomeElement ->
                val outcome = outcomeElement.jsonObject
                PredictionOutcome(
                    id = outcome.string("id").orEmpty(),
                    title = outcome.string("title").orEmpty(),
                    users = outcome.int("users") ?: 0,
                    channelPoints = outcome.long("channel_points") ?: 0L,
                    color = when (outcome.string("color")?.uppercase()) {
                        "BLUE" -> PredictionOutcomeColor.BLUE
                        "PINK" -> PredictionOutcomeColor.PINK
                        else -> PredictionOutcomeColor.UNKNOWN
                    },
                )
            },
            status = when (item.string("status")?.uppercase()) {
                "ACTIVE" -> PredictionStatus.ACTIVE
                "LOCKED" -> PredictionStatus.LOCKED
                "RESOLVED" -> PredictionStatus.RESOLVED
                "CANCELED" -> PredictionStatus.CANCELED
                else -> PredictionStatus.UNKNOWN
            },
            startedAtMillis = startedAt,
            locksAtMillis = startedAt
                .takeIf { it > 0L && predictionWindowSeconds > 0 }
                ?.plus(predictionWindowSeconds * 1_000L),
            lockedAtMillis = item.instantMillis("locked_at"),
            endedAtMillis = item.instantMillis("ended_at"),
            winningOutcomeId = item.string("winning_outcome_id"),
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun responseData(body: String): JsonArray =
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull?.takeUnless { it == "null" }

    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.array(key: String): JsonArray = get(key)?.let { element ->
        runCatching { element.jsonArray }.getOrNull()
    } ?: JsonArray(emptyList())

    private fun JsonObject.instantMillis(key: String): Long? = string(key)?.let { raw ->
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }
}
