package io.ferventio.shared.workspace

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwitchChannelDirectoryException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Twitch Helix HTTP $statusCode")

/** Shared Helix workspace resolver matching Android's channel and moderator bootstrap semantics. */
class TwitchChannelDirectoryClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    suspend fun resolveByLogins(
        authentication: StoredAuthentication,
        logins: List<String>,
    ): List<ChatChannel> {
        val lease = requireAccessLease(authentication)
        val normalizedLogins = WorkspaceChannelBootstrapPolicy.normalizeLogins(logins).take(MAX_LOGINS)
        if (normalizedLogins.isEmpty()) return emptyList()
        val requested = normalizedLogins.toHashSet()

        val response = client.get(TWITCH_USERS_URL) {
            twitchHeaders(
                accessToken = lease.accessToken,
                clientId = lease.session.clientId,
            )
            url {
                normalizedLogins.forEach { login ->
                    parameters.append("login", login)
                }
            }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)

        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
        }.getOrElse {
            throw IllegalStateException("Twitch returned malformed users JSON", it)
        }
        val seenIds = hashSetOf<String>()
        return buildList(data.size) {
            data.forEach { element ->
                val item = element.runCatching { jsonObject }.getOrNull() ?: return@forEach
                val id = item.string("id") ?: return@forEach
                val login = item.string("login")?.lowercase() ?: return@forEach
                if (login !in requested || !seenIds.add(id)) return@forEach
                val displayName = item.string("display_name") ?: login
                add(
                    ChatChannel(
                        id = id,
                        login = login,
                        displayName = displayName,
                        profileImageUrl = item.string("profile_image_url"),
                    ),
                )
            }
        }
    }

    @Throws(Exception::class)
    suspend fun resolveModeratedChannelIds(
        authentication: StoredAuthentication,
    ): Set<String> {
        val lease = requireAccessLease(authentication)
        val response = client.get(TWITCH_MODERATED_CHANNELS_URL) {
            twitchHeaders(
                accessToken = lease.accessToken,
                clientId = lease.session.clientId,
            )
            url {
                parameters.append("user_id", lease.session.userId)
                parameters.append("first", MAX_MODERATED_CHANNELS.toString())
            }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)

        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
        }.getOrElse {
            throw IllegalStateException("Twitch returned malformed moderated-channels JSON", it)
        }
        return buildSet {
            data.forEach { element ->
                element.runCatching { jsonObject }.getOrNull()
                    ?.string("broadcaster_id")
                    ?.let(::add)
            }
        }
    }

    private fun requireAccessLease(authentication: StoredAuthentication) =
        authentication.also {
            AuthenticationPersistenceValidation.requireValid(
                it.backendCredential,
                it.accessLease,
            )
        }.accessLease ?: error("Twitch workspace resolution requires an access lease")

    private fun io.ktor.client.request.HttpRequestBuilder.twitchHeaders(
        accessToken: String,
        clientId: String,
    ) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        header("Client-Id", clientId)
    }

    private fun ensureSuccess(statusCode: Int, body: String) {
        if (statusCode !in 200..299) {
            throw TwitchChannelDirectoryException(
                statusCode = statusCode,
                responseBody = body.take(300),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val MAX_LOGINS = 100
        const val MAX_MODERATED_CHANNELS = 100
        const val TWITCH_USERS_URL = "https://api.twitch.tv/helix/users"
        const val TWITCH_MODERATED_CHANNELS_URL = "https://api.twitch.tv/helix/moderation/channels"
    }
}
