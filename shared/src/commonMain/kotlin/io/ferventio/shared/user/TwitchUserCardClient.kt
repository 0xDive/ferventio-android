package io.ferventio.shared.user

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.PublicChannelRelationship
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchUser
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class UserCardRemoteEnrichment(
    val user: TwitchUser? = null,
    val relationship: PublicChannelRelationship? = null,
)

internal class TwitchUserCardClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    suspend fun enrich(
        authentication: StoredAuthentication,
        userId: String,
        userLogin: String,
        channelLogin: String,
    ): UserCardRemoteEnrichment {
        val user = bestEffort {
            loadUser(
                authentication = authentication,
                userId = userId,
                userLogin = userLogin,
            )
        }
        val relationship = bestEffort {
            loadPublicRelationship(
                userLogin = user?.login?.takeIf(String::isNotBlank) ?: userLogin,
                channelLogin = channelLogin,
            )
        }
        return UserCardRemoteEnrichment(
            user = user,
            relationship = relationship,
        )
    }

    suspend fun loadUser(
        authentication: StoredAuthentication,
        userId: String,
        userLogin: String,
    ): TwitchUser {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for user-card enrichment"
        }
        val normalizedId = userId.trim()
        val normalizedLogin = normalizeOptionalLogin(userLogin)
        require(normalizedId.isNotBlank() || normalizedLogin != null) {
            "Twitch user id or login is required"
        }

        val response = client.get(TWITCH_USERS_URL) {
            header("Client-Id", lease.session.clientId)
            header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
            if (normalizedId.isNotBlank()) {
                parameter("id", normalizedId)
            } else {
                parameter("login", normalizedLogin)
            }
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        val body = response.bodyAsText()
        requireSuccess("Twitch", response.status.value, body)
        val root = parseObject(body, "Twitch")
        val item = root["data"]
            ?.runCatching { jsonArray.firstOrNull()?.jsonObject }
            ?.getOrNull()
            ?: error("Twitch user was not found")

        return TwitchUser(
            id = item.requiredString("id"),
            login = item.requiredString("login"),
            displayName = item.string("display_name")
                ?.takeIf(String::isNotBlank)
                ?: item.requiredString("login"),
            profileImageUrl = item.string("profile_image_url")?.takeIf(String::isNotBlank),
            createdAt = item.string("created_at")?.takeIf(String::isNotBlank),
            broadcasterType = item.string("broadcaster_type")?.takeIf(String::isNotBlank),
            description = item.string("description")?.takeIf(String::isNotBlank),
        )
    }

    suspend fun loadPublicRelationship(
        userLogin: String,
        channelLogin: String,
    ): PublicChannelRelationship {
        val normalizedUser = normalizeRequiredLogin(userLogin, "user")
        val normalizedChannel = normalizeRequiredLogin(channelLogin, "channel")
        val response = client.get(
            "$IVR_SUBAGE_BASE_URL/$normalizedUser/$normalizedChannel",
        ) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio")
        }
        val body = response.bodyAsText()
        requireSuccess("IVR", response.status.value, body)
        val root = parseObject(body, "IVR")
        val meta = root["meta"]?.runCatching { jsonObject }?.getOrNull()
        val cumulative = root["cumulative"]?.runCatching { jsonObject }?.getOrNull()
        return PublicChannelRelationship(
            followedAt = root.string("followedAt")?.takeIf(String::isNotBlank),
            subscriptionStatusHidden = root.boolean("statusHidden"),
            isCurrentlySubscribed = if (root.containsKey("meta")) meta != null else null,
            subscriberMonths = cumulative?.int("months")?.takeIf { it > 0 },
            subscriberTier = meta?.string("tier")?.takeIf(String::isNotBlank),
        )
    }

    fun close() {
        client.close()
    }

    private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun normalizeOptionalLogin(value: String): String? {
        val normalized = value.trim()
            .removePrefix("@")
            .removePrefix("#")
            .lowercase()
        if (normalized.isEmpty()) return null
        require(TWITCH_LOGIN_REGEX.matches(normalized)) { "Invalid Twitch login" }
        return normalized
    }

    private fun normalizeRequiredLogin(value: String, label: String): String =
        requireNotNull(normalizeOptionalLogin(value)) { "Twitch $label login is required" }

    private fun requireSuccess(source: String, status: Int, body: String) {
        if (status in 200..299) return
        val message = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull().orEmpty().ifBlank {
            runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull().orEmpty()
        }.ifBlank {
            body.trim().take(300).ifBlank { "unknown error" }
        }
        error("$source user-card request failed with HTTP $status: $message")
    }

    private fun parseObject(body: String, source: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { error ->
                throw IllegalStateException("$source returned malformed JSON", error)
            }

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.takeIf(String::isNotBlank)
            ?: error("Response is missing $name")

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()

    private fun JsonObject.boolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private companion object {
        const val TWITCH_USERS_URL = "https://api.twitch.tv/helix/users"
        const val IVR_SUBAGE_BASE_URL = "https://api.ivr.fi/v2/twitch/subage"
        val TWITCH_LOGIN_REGEX = Regex("^[a-z0-9_]{1,25}$")
    }
}
