package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.chatBadgeAssetKey
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwitchChatBadgeException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Twitch chat badges HTTP $statusCode")

/** Shared Helix badge catalog loader used by the common chat timeline. */
class TwitchChatBadgeClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    private val json = Json { ignoreUnknownKeys = true }

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
        return parse(body)
    }

    private fun parse(body: String): Map<String, ChatBadgeAsset> {
        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        }.getOrElse {
            throw IllegalStateException("Twitch returned malformed chat-badges JSON", it)
        }
        return buildMap {
            for (setElement in data) {
                val set = setElement.runCatching { jsonObject }.getOrNull() ?: continue
                val setId = set.string("set_id") ?: continue
                val versions = set["versions"]?.runCatching { jsonArray }?.getOrNull() ?: continue
                for (versionElement in versions) {
                    val version = versionElement.runCatching { jsonObject }.getOrNull() ?: continue
                    val id = version.string("id") ?: continue
                    val imageUrl1x = version.string("image_url_1x") ?: continue
                    val imageUrl2x = version.string("image_url_2x") ?: imageUrl1x
                    val imageUrl4x = version.string("image_url_4x") ?: imageUrl2x
                    val asset = ChatBadgeAsset(
                        setId = setId,
                        id = id,
                        imageUrl1x = imageUrl1x,
                        imageUrl2x = imageUrl2x,
                        imageUrl4x = imageUrl4x,
                        title = version.string("title").orEmpty(),
                        description = version.string("description").orEmpty(),
                    )
                    put(chatBadgeAssetKey(setId, id), asset)
                }
            }
        }
    }

    private fun requireAccessLease(authentication: StoredAuthentication) =
        authentication.also {
            AuthenticationPersistenceValidation.requireValid(
                it.backendCredential,
                it.accessLease,
            )
        }.accessLease ?: error("Twitch chat badges require an access lease")

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val TWITCH_GLOBAL_BADGES_URL = "https://api.twitch.tv/helix/chat/badges/global"
        const val TWITCH_CHANNEL_BADGES_URL = "https://api.twitch.tv/helix/chat/badges"
    }
}
