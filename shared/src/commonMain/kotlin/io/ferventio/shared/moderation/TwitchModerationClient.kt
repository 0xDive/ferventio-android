package io.ferventio.shared.moderation

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class TwitchModerationErrorPayload(
    val message: String? = null,
    val error: String? = null,
)

class TwitchModerationMutationException(
    val operation: String,
    val statusCode: Int,
    val twitchMessage: String?,
) : IllegalStateException(
    buildString {
        append("Twitch moderation ")
        append(operation)
        append(" failed with HTTP ")
        append(statusCode)
        twitchMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(": ")
            append(message)
        }
    },
)

class TwitchModerationScopeException(
    val requiredScope: String,
) : IllegalStateException("Twitch moderation requires OAuth scope $requiredScope")

interface TwitchModerationGateway {
    suspend fun banUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        reason: String? = null,
    )

    suspend fun timeoutUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        durationSeconds: Int,
        reason: String? = null,
    )

    suspend fun unbanUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
    )

    suspend fun deleteChatMessage(
        authentication: StoredAuthentication,
        broadcasterId: String,
        messageId: String,
    )

    suspend fun clearChatMessages(
        authentication: StoredAuthentication,
        broadcasterId: String,
    )
}

class TwitchModerationClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TwitchModerationGateway {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    override suspend fun banUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        reason: String?,
    ) {
        updateBanState(authentication, broadcasterId, targetUserId, null, reason, "ban")
    }

    override suspend fun timeoutUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        durationSeconds: Int,
        reason: String?,
    ) {
        require(durationSeconds in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            "Twitch timeout duration must be between $MIN_TIMEOUT_SECONDS and $MAX_TIMEOUT_SECONDS seconds"
        }
        updateBanState(authentication, broadcasterId, targetUserId, durationSeconds, reason, "timeout")
    }

    override suspend fun unbanUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
    ) {
        val context = moderationContext(authentication, broadcasterId, BANNED_USERS_SCOPE)
        val normalizedTargetUserId = requireTargetUserId(
            targetUserId,
            context.broadcasterId,
            context.moderatorId,
        )
        val response = client.delete(MODERATION_BANS_URL) {
            applyAuthentication(context)
            parameter("broadcaster_id", context.broadcasterId)
            parameter("moderator_id", context.moderatorId)
            parameter("user_id", normalizedTargetUserId)
        }
        requireSuccess(response, "unban")
    }

    override suspend fun deleteChatMessage(
        authentication: StoredAuthentication,
        broadcasterId: String,
        messageId: String,
    ) {
        val normalizedMessageId = messageId.trim()
        require(normalizedMessageId.isNotBlank()) { "Twitch moderation messageId must not be blank" }
        deleteChatMessages(authentication, broadcasterId, normalizedMessageId, "delete chat message")
    }

    override suspend fun clearChatMessages(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ) {
        deleteChatMessages(authentication, broadcasterId, null, "clear chat")
    }

    private suspend fun updateBanState(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        durationSeconds: Int?,
        reason: String?,
        operation: String,
    ) {
        val context = moderationContext(authentication, broadcasterId, BANNED_USERS_SCOPE)
        val normalizedTargetUserId = requireTargetUserId(
            targetUserId,
            context.broadcasterId,
            context.moderatorId,
        )
        val normalizedReason = reason?.trim()?.takeIf(String::isNotBlank)
        require(normalizedReason == null || normalizedReason.length <= MAX_REASON_LENGTH) {
            "Twitch moderation reason must not exceed $MAX_REASON_LENGTH characters"
        }
        val body = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("user_id", normalizedTargetUserId)
                    durationSeconds?.let { put("duration", it) }
                    normalizedReason?.let { put("reason", it) }
                },
            )
        }
        val response = client.post(MODERATION_BANS_URL) {
            applyAuthentication(context)
            parameter("broadcaster_id", context.broadcasterId)
            parameter("moderator_id", context.moderatorId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        requireSuccess(response, operation)
    }

    private suspend fun deleteChatMessages(
        authentication: StoredAuthentication,
        broadcasterId: String,
        messageId: String?,
        operation: String,
    ) {
        val context = moderationContext(authentication, broadcasterId, CHAT_MESSAGES_SCOPE)
        val response = client.delete(MODERATION_CHAT_URL) {
            applyAuthentication(context)
            parameter("broadcaster_id", context.broadcasterId)
            parameter("moderator_id", context.moderatorId)
            messageId?.let { parameter("message_id", it) }
        }
        requireSuccess(response, operation)
    }

    private fun moderationContext(
        authentication: StoredAuthentication,
        broadcasterId: String,
        requiredScope: String,
    ): ModerationContext {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val accessLease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for moderation mutations"
        }
        requireScope(accessLease, requiredScope)

        val normalizedBroadcasterId = broadcasterId.trim()
        val moderatorId = accessLease.session.userId.trim()
        val clientId = accessLease.session.clientId.trim()
        require(normalizedBroadcasterId.isNotBlank()) { "Twitch moderation broadcasterId must not be blank" }
        require(moderatorId.isNotBlank()) { "Twitch moderation moderatorId must not be blank" }
        require(clientId.isNotBlank()) { "Twitch moderation clientId must not be blank" }
        require(accessLease.accessToken.isNotBlank()) { "Twitch moderation access token must not be blank" }

        return ModerationContext(
            broadcasterId = normalizedBroadcasterId,
            moderatorId = moderatorId,
            clientId = clientId,
            accessToken = accessLease.accessToken,
        )
    }

    private fun requireScope(accessLease: TwitchAccessLease, requiredScope: String) {
        if (requiredScope !in accessLease.session.scopes) {
            throw TwitchModerationScopeException(requiredScope)
        }
    }

    private fun requireTargetUserId(
        targetUserId: String,
        broadcasterId: String,
        moderatorId: String,
    ): String {
        val normalized = targetUserId.trim()
        require(normalized.isNotBlank()) { "Twitch moderation targetUserId must not be blank" }
        require(normalized != broadcasterId) { "Twitch moderation cannot target the broadcaster" }
        require(normalized != moderatorId) { "Twitch moderation cannot target the authenticated moderator" }
        return normalized
    }

    private fun HttpRequestBuilder.applyAuthentication(context: ModerationContext) {
        header("Client-Id", context.clientId)
        header(HttpHeaders.Authorization, "Bearer ${context.accessToken}")
    }

    private suspend fun requireSuccess(response: HttpResponse, operation: String) {
        if (response.status.value in 200..299) return

        val body = response.bodyAsText()
        val payload = runCatching {
            json.decodeFromString(TwitchModerationErrorPayload.serializer(), body)
        }.getOrNull()
        val message = payload?.message?.takeIf(String::isNotBlank)
            ?: payload?.error?.takeIf(String::isNotBlank)
            ?: body.trim().takeIf(String::isNotBlank)
        throw TwitchModerationMutationException(
            operation = operation,
            statusCode = response.status.value,
            twitchMessage = message,
        )
    }

    private data class ModerationContext(
        val broadcasterId: String,
        val moderatorId: String,
        val clientId: String,
        val accessToken: String,
    )

    private companion object {
        const val MODERATION_BANS_URL = "https://api.twitch.tv/helix/moderation/bans"
        const val MODERATION_CHAT_URL = "https://api.twitch.tv/helix/moderation/chat"
        const val BANNED_USERS_SCOPE = "moderator:manage:banned_users"
        const val CHAT_MESSAGES_SCOPE = "moderator:manage:chat_messages"
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 1_209_600
        const val MAX_REASON_LENGTH = 500
    }
}
