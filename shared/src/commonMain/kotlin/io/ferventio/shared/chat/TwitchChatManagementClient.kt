package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ModerationChatSettings
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class TwitchChattersSnapshot(
    val total: Int,
    val users: List<ModerationUser>,
)

class TwitchChatManagementScopeException(
    val requiredScope: String,
) : IllegalStateException("Twitch chat management requires OAuth scope $requiredScope")

class TwitchChatManagementException(
    val operation: String,
    val statusCode: Int,
    val twitchMessage: String?,
) : IllegalStateException(
    buildString {
        append("Twitch chat management ")
        append(operation)
        append(" failed with HTTP ")
        append(statusCode)
        twitchMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(": ")
            append(message)
        }
    },
)

class TwitchChatManagementClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    suspend fun getChatSettings(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ): ModerationChatSettings {
        val context = authenticationContext(authentication)
        val normalizedBroadcasterId = broadcasterId.trim()
        require(normalizedBroadcasterId.isNotEmpty()) {
            "Twitch chat settings broadcasterId must not be blank"
        }
        val response = client.get(CHAT_SETTINGS_URL) {
            applyAuthentication(context)
            parameter("broadcaster_id", normalizedBroadcasterId)
        }
        val body = response.bodyAsText()
        requireSuccess(response, body, "get chat settings")
        return parseChatSettings(body, normalizedBroadcasterId)
    }

    suspend fun updateChatSettings(
        authentication: StoredAuthentication,
        broadcasterId: String,
        slowMode: Boolean? = null,
        slowModeWaitSeconds: Int? = null,
        followerMode: Boolean? = null,
        followerModeDurationMinutes: Int? = null,
        subscriberMode: Boolean? = null,
        emoteMode: Boolean? = null,
        uniqueChatMode: Boolean? = null,
    ): ModerationChatSettings {
        val context = authenticationContext(authentication, MANAGE_CHAT_SETTINGS_SCOPE)
        val normalizedBroadcasterId = broadcasterId.trim()
        require(normalizedBroadcasterId.isNotEmpty()) {
            "Twitch chat settings broadcasterId must not be blank"
        }
        slowModeWaitSeconds?.let { seconds ->
            require(seconds in 3..120) { "Slow mode wait time must be between 3 and 120 seconds" }
        }
        followerModeDurationMinutes?.let { minutes ->
            require(minutes in 0..129_600) {
                "Follower mode duration must be between 0 and 129600 minutes"
            }
        }
        require(
            listOf(
                slowMode,
                slowModeWaitSeconds,
                followerMode,
                followerModeDurationMinutes,
                subscriberMode,
                emoteMode,
                uniqueChatMode,
            ).any { it != null },
        ) { "At least one chat setting must be provided" }

        val payload = buildJsonObject {
            slowMode?.let { put("slow_mode", JsonPrimitive(it)) }
            slowModeWaitSeconds?.let { put("slow_mode_wait_time", JsonPrimitive(it)) }
            followerMode?.let { put("follower_mode", JsonPrimitive(it)) }
            followerModeDurationMinutes?.let { put("follower_mode_duration", JsonPrimitive(it)) }
            subscriberMode?.let { put("subscriber_mode", JsonPrimitive(it)) }
            emoteMode?.let { put("emote_mode", JsonPrimitive(it)) }
            uniqueChatMode?.let { put("unique_chat_mode", JsonPrimitive(it)) }
        }
        val response = client.patch(CHAT_SETTINGS_URL) {
            applyAuthentication(context)
            parameter("broadcaster_id", normalizedBroadcasterId)
            parameter("moderator_id", context.userId)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        requireSuccess(response, body, "update chat settings")
        return runCatching { parseChatSettings(body, normalizedBroadcasterId) }
            .getOrElse { getChatSettings(authentication, normalizedBroadcasterId) }
    }

    suspend fun getChatters(
        authentication: StoredAuthentication,
        broadcasterId: String,
        first: Int = DEFAULT_CHATTERS_LIMIT,
    ): TwitchChattersSnapshot {
        val context = authenticationContext(authentication, READ_CHATTERS_SCOPE)
        val normalizedBroadcasterId = broadcasterId.trim()
        require(normalizedBroadcasterId.isNotEmpty()) {
            "Twitch chatters broadcasterId must not be blank"
        }
        val requested = first.coerceIn(1, MAX_CHATTERS_LIMIT)
        val users = mutableListOf<ModerationUser>()
        var total = 0
        var cursor: String? = null
        do {
            val response = client.get(CHATTERS_URL) {
                applyAuthentication(context)
                parameter("broadcaster_id", normalizedBroadcasterId)
                parameter("moderator_id", context.userId)
                parameter("first", minOf(1_000, requested - users.size).coerceAtLeast(1))
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            requireSuccess(response, body, "get chatters")
            val root = parseRoot(body)
            total = root.int("total") ?: total
            users += (root["data"] as? JsonArray).orEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val id = item.string("user_id").orEmpty()
                val login = item.string("user_login").orEmpty()
                val displayName = item.string("user_name").orEmpty()
                if (id.isBlank() || login.isBlank()) null else {
                    ModerationUser(
                        id = id,
                        login = login,
                        displayName = displayName.ifBlank { login },
                        group = ModerationUserGroup.VIEWER,
                    )
                }
            }
            cursor = (root["pagination"] as? JsonObject)
                ?.string("cursor")
                ?.takeIf(String::isNotBlank)
        } while (cursor != null && users.size < requested)

        return TwitchChattersSnapshot(
            total = total,
            users = users.distinctBy { user -> user.id }.take(requested),
        )
    }

    fun close() {
        client.close()
    }

    private fun authenticationContext(
        authentication: StoredAuthentication,
        requiredScope: String? = null,
    ): AuthenticationContext {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for chat management"
        }
        requiredScope?.let { scope ->
            if (scope !in lease.session.scopes) throw TwitchChatManagementScopeException(scope)
        }
        return AuthenticationContext(
            clientId = lease.session.clientId.trim().also {
                require(it.isNotEmpty()) { "Twitch clientId must not be blank" }
            },
            userId = lease.session.userId.trim().also {
                require(it.isNotEmpty()) { "Twitch userId must not be blank" }
            },
            accessToken = lease.accessToken.trim().also {
                require(it.isNotEmpty()) { "Twitch access token must not be blank" }
            },
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthentication(
        context: AuthenticationContext,
    ) {
        header("Client-Id", context.clientId)
        header(HttpHeaders.Authorization, "Bearer " + context.accessToken)
    }

    private fun parseChatSettings(
        body: String,
        broadcasterId: String,
    ): ModerationChatSettings {
        val root = parseRoot(body)
        val item = (root["data"] as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: error("Twitch chat settings response did not contain data")
        return ModerationChatSettings(
            channelId = item.string("broadcaster_id") ?: broadcasterId,
            slowMode = item.boolean("slow_mode") ?: false,
            slowModeWaitSeconds = item.int("slow_mode_wait_time") ?: 30,
            followerMode = item.boolean("follower_mode") ?: false,
            followerModeDurationMinutes = item.int("follower_mode_duration") ?: 0,
            subscriberMode = item.boolean("subscriber_mode") ?: false,
            emoteMode = item.boolean("emote_mode") ?: false,
            uniqueChatMode = item.boolean("unique_chat_mode") ?: false,
        )
    }

    private fun parseRoot(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body) as? JsonObject }
            .getOrNull()
            ?: error("Twitch returned malformed chat management response")

    private suspend fun requireSuccess(
        response: HttpResponse,
        body: String,
        operation: String,
    ) {
        if (response.status.value in 200..299) return
        val message = runCatching {
            val root = parseRoot(body)
            root.string("message") ?: root.string("error")
        }.getOrNull() ?: body.trim().take(300).takeIf(String::isNotBlank)
        throw TwitchChatManagementException(
            operation = operation,
            statusCode = response.status.value,
            twitchMessage = message,
        )
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private data class AuthenticationContext(
        val clientId: String,
        val userId: String,
        val accessToken: String,
    )

    private companion object {
        const val CHAT_SETTINGS_URL = "https://api.twitch.tv/helix/chat/settings"
        const val CHATTERS_URL = "https://api.twitch.tv/helix/chat/chatters"
        const val MANAGE_CHAT_SETTINGS_SCOPE = "moderator:manage:chat_settings"
        const val READ_CHATTERS_SCOPE = "moderator:read:chatters"
        const val DEFAULT_CHATTERS_LIMIT = 1_000
        const val MAX_CHATTERS_LIMIT = 5_000
    }
}
