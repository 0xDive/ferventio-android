package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatSendResult
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class TwitchChatMessageScopeException(
    val requiredScope: String,
) : IllegalStateException("Twitch chat sending requires OAuth scope $requiredScope")

class TwitchChatMessageMutationException(
    val statusCode: Int,
    val twitchMessage: String,
    val retryAtMillis: Long? = null,
) : IllegalStateException(
    if (statusCode == 429) {
        "Twitch chat rate limit: $twitchMessage"
    } else {
        "Twitch chat send failed with HTTP $statusCode: $twitchMessage"
    },
)

class TwitchChatMessageDroppedException(
    val code: String?,
    val twitchMessage: String,
) : IllegalStateException(twitchMessage)

interface TwitchChatMessageGateway {
    suspend fun sendMessage(
        authentication: StoredAuthentication,
        broadcasterId: String,
        message: String,
        replyParentMessageId: String? = null,
    ): ChatSendResult
}

class TwitchChatMessageClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val currentEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TwitchChatMessageGateway {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    override suspend fun sendMessage(
        authentication: StoredAuthentication,
        broadcasterId: String,
        message: String,
        replyParentMessageId: String?,
    ): ChatSendResult {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for chat sending"
        }
        if (WRITE_CHAT_SCOPE !in lease.session.scopes) {
            throw TwitchChatMessageScopeException(WRITE_CHAT_SCOPE)
        }

        val normalizedBroadcasterId = broadcasterId.trim()
        val normalizedSenderId = lease.session.userId.trim()
        val normalizedClientId = lease.session.clientId.trim()
        val normalizedToken = lease.accessToken.trim()
        val normalizedMessage = message.trim()
        val normalizedReplyParentMessageId = replyParentMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        require(normalizedBroadcasterId.isNotEmpty()) {
            "Twitch chat broadcasterId must not be blank"
        }
        require(normalizedSenderId.isNotEmpty()) {
            "Twitch chat senderId must not be blank"
        }
        require(normalizedClientId.isNotEmpty()) {
            "Twitch chat clientId must not be blank"
        }
        require(normalizedToken.isNotEmpty()) {
            "Twitch chat access token must not be blank"
        }
        require(normalizedMessage.isNotEmpty()) {
            "Twitch chat message must not be blank"
        }
        require(normalizedMessage.length <= MAX_MESSAGE_LENGTH) {
            "Twitch chat message must not exceed $MAX_MESSAGE_LENGTH characters"
        }

        val request = TwitchChatMessageRequest(
            broadcasterId = normalizedBroadcasterId,
            senderId = normalizedSenderId,
            message = normalizedMessage,
            replyParentMessageId = normalizedReplyParentMessageId,
        )
        val response = client.post(SEND_CHAT_MESSAGE_URL) {
            header("Client-Id", normalizedClientId)
            header(HttpHeaders.Authorization, "Bearer $normalizedToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(TwitchChatMessageRequest.serializer(), request))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchChatMessageMutationException(
                statusCode = response.status.value,
                twitchMessage = decodeApiMessage(body),
                retryAtMillis = retryAtMillis(response),
            )
        }

        val payload = runCatching {
            json.decodeFromString(TwitchChatMessageResponse.serializer(), body)
        }.getOrElse { error ->
            throw IllegalStateException("Twitch returned malformed chat send response", error)
        }
        val result = payload.data.firstOrNull()
            ?: throw IllegalStateException("Twitch chat send response did not contain data")
        if (!result.isSent) {
            throw TwitchChatMessageDroppedException(
                code = result.dropReason?.code,
                twitchMessage = result.dropReason?.message
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: "Twitch rejected the chat message",
            )
        }
        return ChatSendResult(
            messageId = result.messageId?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun decodeApiMessage(body: String): String = runCatching {
        json.decodeFromString(TwitchApiError.serializer(), body).message
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }.getOrNull() ?: body.trim().take(300).ifBlank { "unknown Twitch error" }

    private fun retryAtMillis(response: HttpResponse): Long? {
        val resetEpochSeconds = response.headers["Ratelimit-Reset"]?.toLongOrNull()
        if (resetEpochSeconds != null) return resetEpochSeconds * 1_000L
        val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
        return retryAfterSeconds?.let { currentEpochMillis() + it * 1_000L }
    }

    private companion object {
        const val SEND_CHAT_MESSAGE_URL = "https://api.twitch.tv/helix/chat/messages"
        const val WRITE_CHAT_SCOPE = "user:write:chat"
        const val MAX_MESSAGE_LENGTH = 500
    }
}

@Serializable
private data class TwitchChatMessageRequest(
    @kotlinx.serialization.SerialName("broadcaster_id")
    val broadcasterId: String,
    @kotlinx.serialization.SerialName("sender_id")
    val senderId: String,
    val message: String,
    @kotlinx.serialization.SerialName("reply_parent_message_id")
    val replyParentMessageId: String? = null,
)

@Serializable
private data class TwitchChatMessageResponse(
    val data: List<TwitchChatMessageResponseItem> = emptyList(),
)

@Serializable
private data class TwitchChatMessageResponseItem(
    @kotlinx.serialization.SerialName("message_id")
    val messageId: String? = null,
    @kotlinx.serialization.SerialName("is_sent")
    val isSent: Boolean = false,
    @kotlinx.serialization.SerialName("drop_reason")
    val dropReason: TwitchChatDropReason? = null,
)

@Serializable
private data class TwitchChatDropReason(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
private data class TwitchApiError(
    val message: String? = null,
)
