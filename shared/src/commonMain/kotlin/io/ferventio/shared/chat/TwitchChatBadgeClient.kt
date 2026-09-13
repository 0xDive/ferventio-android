package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlin.Throws

class TwitchChatBadgeException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Twitch chat badges HTTP $statusCode")

/** Shared Helix badge catalog loader used by the common chat timeline. */
class TwitchChatBadgeClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    @Throws(Exception::class)
    suspend fun loadGlobal(
        authentication: StoredAuthentication,
    ): Map<String, ChatBadgeAsset> = load(
        authentication = authentication,
        url = TWITCH_GLOBAL_BADGES_URL,
        broadcasterId = null,
    )

    @Throws(Exception::class)
    suspend fun loadChannel(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ): Map<String, ChatBadgeAsset> {
        val normalizedBroadcasterId = broadcasterId.trim()
        if (normalizedBroadcasterId.isEmpty()) return emptyMap()
        return load(
            authentication = authentication,
            url = TWITCH_CHANNEL_BADGES_URL,
            broadcasterId = normalizedBroadcasterId,
        )
    }

    private suspend fun load(
        authentication: StoredAuthentication,
        url: String,
        broadcasterId: String?,
    ): Map<String, ChatBadgeAsset> {
        val lease = requireAccessLease(authentication)
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
            header("Client-Id", lease.session.clientId)
            broadcasterId?.let { id ->
                url { parameters.append("broadcaster_id", id) }
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchChatBadgeException(
                statusCode = response.status.value,
                responseBody = body.take(300),
            )
        }
        return TwitchChatBadgeCatalogParser.parse(body)
    }

    private fun requireAccessLease(authentication: StoredAuthentication) =
        authentication.also {
            AuthenticationPersistenceValidation.requireValid(
                it.backendCredential,
                it.accessLease,
            )
        }.accessLease ?: error("Twitch chat badges require an access lease")

    private companion object {
        const val TWITCH_GLOBAL_BADGES_URL = "https://api.twitch.tv/helix/chat/badges/global"
        const val TWITCH_CHANNEL_BADGES_URL = "https://api.twitch.tv/helix/chat/badges"
    }
}
