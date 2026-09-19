package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatAssetResolver
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class TwitchUserEmoteCatalogClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    suspend fun load(
        authentication: StoredAuthentication,
        broadcasterId: String? = null,
    ): List<ThirdPartyEmoteAsset> {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease)
        val session = lease.session
        if (USER_EMOTES_SCOPE !in session.scopes) return emptyList()

        val result = linkedMapOf<String, ThirdPartyEmoteAsset>()
        var cursor: String? = null
        do {
            val response = client.get(USER_EMOTES_URL) {
                header("Client-Id", session.clientId)
                header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                parameter("user_id", session.userId)
                broadcasterId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { parameter("broadcaster_id", it) }
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            if (response.status.value == 401 || response.status.value == 403) {
                return emptyList()
            }
            require(response.status.value in 200..299) {
                "Twitch user emotes HTTP ${response.status.value}: ${body.trim().take(200)}"
            }
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }
                .getOrNull() ?: return result.values.toList()
            val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
            data.forEach { element ->
                val emote = element as? JsonObject ?: return@forEach
                val id = emote.string("id").orEmpty()
                val code = emote.string("name").orEmpty()
                if (id.isBlank() || code.isBlank()) return@forEach
                val ownerId = emote.string("owner_id")
                    ?.takeUnless { it == "0" }
                val formats = emote.stringArray("format")
                val animated = "animated" in formats
                val image1x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "1.0",
                    animatedAvailable = animated,
                ) ?: return@forEach
                val image2x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "2.0",
                    animatedAvailable = animated,
                ) ?: image1x
                val image3x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "3.0",
                    animatedAvailable = animated,
                ) ?: image2x
                result["twitch:$id"] = ThirdPartyEmoteAsset(
                    id = id,
                    code = code,
                    provider = TWITCH_PROVIDER,
                    imageType = if (animated) "gif" else "png",
                    animated = animated,
                    imageUrl1x = image1x,
                    imageUrl2x = image2x,
                    imageUrl3x = image3x,
                    scope = if (ownerId == null) EmoteScope.GLOBAL else EmoteScope.CHANNEL,
                    channelId = ownerId,
                )
            }
            cursor = (root["pagination"] as? JsonObject)
                ?.string("cursor")
                ?.takeIf(String::isNotEmpty)
        } while (cursor != null)

        return result.values.toList()
    }

    fun close() {
        client.close()
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.stringArray(name: String): Set<String> =
        (this[name] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val USER_EMOTES_SCOPE = "user:read:emotes"
        const val TWITCH_PROVIDER = "twitch"
        const val USER_EMOTES_URL = "https://api.twitch.tv/helix/chat/emotes/user"
    }
}
