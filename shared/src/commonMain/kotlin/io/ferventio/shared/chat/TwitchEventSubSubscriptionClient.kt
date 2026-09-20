package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
data class TwitchEventSubSubscriptionSnapshot(
    val id: String,
    val status: String,
    val type: String,
    val version: String = "1",
    val transport: TwitchEventSubSubscriptionTransportSnapshot,
)

@Serializable
data class TwitchEventSubSubscriptionTransportSnapshot(
    val method: String,
    val session_id: String? = null,
    val connected_at: String? = null,
    val disconnected_at: String? = null,
)

@Serializable
private data class TwitchEventSubSubscriptionListPayload(
    val data: List<TwitchEventSubSubscriptionSnapshot> = emptyList(),
    val pagination: TwitchEventSubPagination = TwitchEventSubPagination(),
)

@Serializable
private data class TwitchEventSubPagination(
    val cursor: String? = null,
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
        val accessLease = requireAccessLease(authentication)
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
            twitchHeaders(accessLease)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    TwitchEventSubSubscriptionRequest.serializer(),
                    request,
                ),
            )
        }
        val body = response.bodyAsText()
        requireSuccess(response.status.value, body)
        return request
    }

    suspend fun listSubscriptions(
        authentication: StoredAuthentication,
        status: String? = null,
    ): List<TwitchEventSubSubscriptionSnapshot> {
        val accessLease = requireAccessLease(authentication)
        val normalizedStatus = status?.trim()?.takeIf(String::isNotEmpty)
        val subscriptions = mutableListOf<TwitchEventSubSubscriptionSnapshot>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        repeat(MAX_LIST_PAGES) {
            val response = client.get(EVENTSUB_SUBSCRIPTIONS_URL) {
                twitchHeaders(accessLease)
                normalizedStatus?.let { parameter("status", it) }
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            requireSuccess(response.status.value, body)
            val payload = json.decodeFromString(
                TwitchEventSubSubscriptionListPayload.serializer(),
                body,
            )
            subscriptions += payload.data

            val nextCursor = payload.pagination.cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return subscriptions
            check(seenCursors.add(nextCursor)) {
                "Twitch EventSub pagination repeated cursor"
            }
            cursor = nextCursor
        }

        error("Twitch EventSub pagination exceeded safety limit")
    }

    suspend fun listEnabledWebSocketSubscriptions(
        authentication: StoredAuthentication,
    ): List<TwitchEventSubSubscriptionSnapshot> =
        listSubscriptions(authentication, status = ENABLED_STATUS)
            .filter { subscription ->
                subscription.transport.method.equals(WEBSOCKET_METHOD, ignoreCase = true) &&
                    !subscription.transport.session_id.isNullOrBlank()
            }

    /**
     * Deletes one EventSub subscription.
     *
     * Returns false when Twitch already reports the subscription as absent so cleanup can be
     * safely retried after a partial run.
     */
    suspend fun deleteSubscription(
        authentication: StoredAuthentication,
        subscriptionId: String,
    ): Boolean {
        val accessLease = requireAccessLease(authentication)
        val normalizedId = subscriptionId.trim()
        require(normalizedId.isNotEmpty()) { "EventSub subscription id must not be blank" }

        val response = client.delete(EVENTSUB_SUBSCRIPTIONS_URL) {
            twitchHeaders(accessLease)
            parameter("id", normalizedId)
        }
        if (response.status.value == NOT_FOUND_STATUS_CODE) return false
        val body = response.bodyAsText()
        requireSuccess(response.status.value, body)
        return true
    }

    private fun requireAccessLease(authentication: StoredAuthentication): TwitchAccessLease {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        return requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for EventSub subscriptions"
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.twitchHeaders(
        accessLease: TwitchAccessLease,
    ) {
        header("Client-Id", accessLease.session.clientId)
        header(HttpHeaders.Authorization, "Bearer ${accessLease.accessToken}")
    }

    private fun requireSuccess(statusCode: Int, body: String) {
        if (statusCode in 200..299) return
        val payload = runCatching {
            json.decodeFromString(TwitchEventSubErrorPayload.serializer(), body)
        }.getOrNull()
        val message = payload?.message?.takeIf(String::isNotBlank)
            ?: payload?.error?.takeIf(String::isNotBlank)
            ?: body.trim().takeIf(String::isNotBlank)
        throw TwitchEventSubSubscriptionException(
            statusCode = statusCode,
            twitchMessage = message,
        )
    }

    private companion object {
        const val EVENTSUB_SUBSCRIPTIONS_URL =
            "https://api.twitch.tv/helix/eventsub/subscriptions"
        const val ENABLED_STATUS = "enabled"
        const val WEBSOCKET_METHOD = "websocket"
        const val MAX_LIST_PAGES = 25
        const val NOT_FOUND_STATUS_CODE = 404
    }
}
