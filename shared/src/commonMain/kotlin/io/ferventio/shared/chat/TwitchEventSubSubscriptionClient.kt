package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TwitchEventSubSubscriptionRequest(
    val type: String,
    val version: String = "1",
    val condition: Map<String, String>,
    val transport: TwitchEventSubTransport,
)

@Serializable
data class TwitchEventSubTransport(
    val method: String = "websocket",
    val session_id: String,
)

@Serializable
private data class TwitchEventSubErrorPayload(
    val message: String? = null,
    val error: String? = null,
)

class TwitchEventSubSubscriptionException(
    val statusCode: Int,
    val twitchMessage: String?,
) : IllegalStateException(
    buildString {
        append("Twitch EventSub subscription failed with HTTP ")
        append(statusCode)
        twitchMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(": ")
            append(message)
        }
    },
)

class TwitchEventSubSubscriptionClient(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    suspend fun createSubscription(
        authentication: StoredAuthentication,
        sessionId: String,
        broadcasterId: String,
        type: String,
        version: String = "1",
        identityConditionKey: String? = "user_id",
    ): TwitchEventSubSubscriptionRequest {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val accessLease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for EventSub subscriptions"
        }
        val session = accessLease.session
        val normalizedSessionId = sessionId.trim()
        val normalizedBroadcasterId = broadcasterId.trim()
        val normalizedType = type.trim()
        val normalizedVersion = version.trim()
        require(normalizedSessionId.isNotBlank()) { "EventSub sessionId must not be blank" }
        require(normalizedBroadcasterId.isNotBlank()) { "EventSub broadcasterId must not be blank" }
        require(normalizedType.isNotBlank()) { "EventSub type must not be blank" }
        require(normalizedVersion.isNotBlank()) { "EventSub version must not be blank" }

        val condition = linkedMapOf(
            "broadcaster_user_id" to normalizedBroadcasterId,
        )
        identityConditionKey
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { key -> condition[key] = session.userId }

        val request = TwitchEventSubSubscriptionRequest(
            type = normalizedType,
            version = normalizedVersion,
            condition = condition,
            transport = TwitchEventSubTransport(session_id = normalizedSessionId),
        )
        val response = client.post(EVENTSUB_SUBSCRIPTIONS_URL) {
            header("Client-Id", session.clientId)
            header(HttpHeaders.Authorization, "Bearer ${accessLease.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    TwitchEventSubSubscriptionRequest.serializer(),
                    request,
                ),
            )
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val payload = runCatching {
                json.decodeFromString(TwitchEventSubErrorPayload.serializer(), body)
            }.getOrNull()
            val message = payload?.message?.takeIf(String::isNotBlank)
                ?: payload?.error?.takeIf(String::isNotBlank)
                ?: body.trim().takeIf(String::isNotBlank)
            throw TwitchEventSubSubscriptionException(
                statusCode = response.status.value,
                twitchMessage = message,
            )
        }
        return request
    }

    private companion object {
        const val EVENTSUB_SUBSCRIPTIONS_URL =
            "https://api.twitch.tv/helix/eventsub/subscriptions"
    }
}
