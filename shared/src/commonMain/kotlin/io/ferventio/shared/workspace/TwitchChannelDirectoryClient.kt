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

/** Shared Helix user resolver matching Android's saved-login workspace bootstrap. */
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
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = authentication.accessLease
            ?: error("Twitch channel resolution requires an access lease")
        val normalizedLogins = WorkspaceChannelBootstrapPolicy.normalizeLogins(logins).take(MAX_LOGINS)
        if (normalizedLogins.isEmpty()) return emptyList()
        val requested = normalizedLogins.toHashSet()

        val response = client.get(TWITCH_USERS_URL) {
            header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
            header("Client-Id", lease.session.clientId)
            url {
                normalizedLogins.forEach { login ->
                    parameters.append("login", login)
                }
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchChannelDirectoryException(
                statusCode = response.status.value,
                responseBody = body.take(300),
            )
        }

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

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val MAX_LOGINS = 100
        const val TWITCH_USERS_URL = "https://api.twitch.tv/helix/users"
    }
}
