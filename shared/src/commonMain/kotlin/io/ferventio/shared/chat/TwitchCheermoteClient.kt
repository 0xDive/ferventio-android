package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwitchCheermoteException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Twitch cheermotes HTTP $statusCode")

/** Shared Helix Cheermote catalog loader used by the common chat timeline. */
class TwitchCheermoteClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    suspend fun load(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ): Map<String, List<CheermoteAsset>> {
        val lease = requireAccessLease(authentication)
        val normalizedBroadcasterId = broadcasterId.trim()
        val response = client.get(TWITCH_CHEERMOTES_URL) {
            header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
            header("Client-Id", lease.session.clientId)
            normalizedBroadcasterId.takeIf(String::isNotEmpty)?.let { id ->
                url { parameters.append("broadcaster_id", id) }
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchCheermoteException(
                statusCode = response.status.value,
                responseBody = body.take(300),
            )
        }
        return parse(body)
    }

    private fun parse(body: String): Map<String, List<CheermoteAsset>> {
        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        }.getOrElse {
            throw IllegalStateException("Twitch returned malformed cheermotes JSON", it)
        }
        return buildMap {
            for (element in data) {
                val cheermote = element.runCatching { jsonObject }.getOrNull() ?: continue
                val prefix = cheermote.string("prefix") ?: continue
                val tiers = cheermote["tiers"]?.runCatching { jsonArray }?.getOrNull() ?: continue
                val assets = tiers.mapNotNull { tierElement ->
                    val tier = tierElement.runCatching { jsonObject }.getOrNull()
                        ?: return@mapNotNull null
                    if (tier.boolean("can_cheer") == false) return@mapNotNull null
                    val tierId = tier.intOrString("id") ?: return@mapNotNull null
                    val minBits = tier.intOrString("min_bits") ?: tierId
                    val images = tier["images"] as? JsonObject
                    val dark = images?.get("dark") as? JsonObject
                    val animated = dark?.get("animated") as? JsonObject
                    val static = dark?.get("static") as? JsonObject
                    val animatedImageUrl = animated.preferredImageUrl()
                    val staticImageUrl = static.preferredImageUrl()
                    if (animatedImageUrl == null && staticImageUrl == null) return@mapNotNull null
                    CheermoteAsset(
                        prefix = prefix,
                        minBits = minBits,
                        tier = tierId,
                        color = tier.string("color").orEmpty(),
                        animatedImageUrl = animatedImageUrl,
                        staticImageUrl = staticImageUrl,
                    )
                }.sortedBy(CheermoteAsset::minBits)
                if (assets.isNotEmpty()) put(prefix.lowercase(), assets)
            }
        }
    }

    private fun requireAccessLease(authentication: StoredAuthentication) =
        authentication.also {
            AuthenticationPersistenceValidation.requireValid(
                it.backendCredential,
                it.accessLease,
            )
        }.accessLease ?: error("Twitch cheermotes require an access lease")

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun JsonObject.intOrString(name: String): Int? =
        (this[name] as? JsonPrimitive)?.let { primitive ->
            primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
        }

    private fun JsonObject.boolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject?.preferredImageUrl(): String? {
        if (this == null) return null
        return sequenceOf("2", "1.5", "1", "3", "4")
            .mapNotNull(::string)
            .firstOrNull()
            ?: values.asSequence()
                .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull?.trim() }
                .firstOrNull(String::isNotEmpty)
    }

    private companion object {
        const val TWITCH_CHEERMOTES_URL = "https://api.twitch.tv/helix/bits/cheermotes"
    }
}
