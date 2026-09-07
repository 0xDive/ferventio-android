package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatSendResult
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.ReplyContext
import io.ferventio.app.domain.StoredAuthentication
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared outgoing-chat runtime mirroring the Android optimistic send/retry flow.
 *
 * EventSub remains the canonical source for rendered Twitch fragments/badges. A local optimistic
 * message is shown immediately and later reconciled with the EventSub echo through message_id.
 */
class TwitchChatMessageRuntime(
    private val chatState: ChatRuntimeStateHolder,
    private val gateway: TwitchChatMessageGateway = TwitchChatMessageClient(),
    private val currentEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val localIdMutex = Mutex()
    private var localSequence = 0L

    suspend fun send(
        authentication: StoredAuthentication,
        channel: ChatChannel,
        message: String,
        replyParentMessageId: String? = null,
    ): ChatSendResult {
        val normalizedText = message.trim()
        require(normalizedText.isNotEmpty()) { "Chat message must not be blank" }
        require(normalizedText.length <= MAX_MESSAGE_LENGTH) {
            "Chat message must not exceed $MAX_MESSAGE_LENGTH characters"
        }
        val lease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for chat sending"
        }
        val localMessageId = nextLocalMessageId()
        val optimistic = optimisticMessage(
            channel = channel,
            authentication = authentication,
            localMessageId = localMessageId,
            text = normalizedText,
            replyParentMessageId = replyParentMessageId,
        )
        chatState.append(optimistic)
        return performSend(
            authentication = authentication,
            channel = channel,
            localMessageId = localMessageId,
            text = normalizedText,
            replyParentMessageId = optimistic.replyParentMessageId,
            authenticatedUserId = lease.session.userId,
        )
    }

    suspend fun retry(
        authentication: StoredAuthentication,
        channel: ChatChannel,
        failedMessage: ChatMessage,
    ): ChatSendResult {
        require(failedMessage.channelId == channel.id) {
            "Outgoing message does not belong to the selected channel"
        }
        require(failedMessage.outgoingState == OutgoingMessageState.FAILED) {
            "Only failed outgoing messages can be retried"
        }
        require(failedMessage.clientNonce != null || failedMessage.id.startsWith(LOCAL_ID_PREFIX)) {
            "Only locally-created outgoing messages can be retried"
        }
        chatState.markOutgoingSending(channel.id, failedMessage.id)
        return performSend(
            authentication = authentication,
            channel = channel,
            localMessageId = failedMessage.id,
            text = failedMessage.text,
            replyParentMessageId = failedMessage.replyParentMessageId,
            authenticatedUserId = authentication.accessLease?.session?.userId.orEmpty(),
        )
    }

    private suspend fun performSend(
        authentication: StoredAuthentication,
        channel: ChatChannel,
        localMessageId: String,
        text: String,
        replyParentMessageId: String?,
        authenticatedUserId: String,
    ): ChatSendResult {
        try {
            val result = gateway.sendMessage(
                authentication = authentication,
                broadcasterId = channel.id,
                message = text,
                replyParentMessageId = replyParentMessageId,
            )
            val serverMessageId = result.messageId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: throw IllegalStateException("Twitch chat send response did not contain message_id")
            chatState.markOutgoingSent(channel.id, localMessageId, serverMessageId)
            return result
        } catch (cancelled: CancellationException) {
            chatState.markOutgoingFailed(
                channel.id,
                localMessageId,
                "Message sending was cancelled",
            )
            throw cancelled
        } catch (error: Throwable) {
            chatState.markOutgoingFailed(channel.id, localMessageId, error.message)
            val apiError = error.findChatMessageCause<TwitchChatMessageMutationException>()
            if (apiError?.statusCode == 401) {
                chatState.markAuthenticationRequired(apiError.message)
            }
            throw error
        }
    }

    private fun optimisticMessage(
        channel: ChatChannel,
        authentication: StoredAuthentication,
        localMessageId: String,
        text: String,
        replyParentMessageId: String?,
    ): ChatMessage {
        val session = requireNotNull(authentication.accessLease).session
        val normalizedReplyId = replyParentMessageId?.trim()?.takeIf(String::isNotEmpty)
        val parent = normalizedReplyId?.let { parentId ->
            chatState.messages(channel.id).firstOrNull { it.id == parentId }
        }
        val nowMillis = currentEpochMillis()
        val now = Clock.System.now()
        return ChatMessage(
            id = localMessageId,
            channelId = channel.id,
            channelLogin = channel.login,
            author = ChatAuthor(
                id = session.userId,
                login = session.login,
                displayName = session.login,
            ),
            text = text,
            fragments = listOf(ChatFragment.Text(text)),
            timestamp = now.toString(),
            timestampMillis = nowMillis,
            reply = normalizedReplyId?.let { parentId ->
                ReplyContext(
                    parentMessageId = parentId,
                    parentMessageBody = parent?.text,
                    parentUserId = parent?.userId,
                    parentUserLogin = parent?.userLogin,
                    parentUserName = parent?.userDisplayName,
                    threadMessageId = parent?.reply?.threadMessageId ?: parent?.id,
                    threadUserId = parent?.reply?.threadUserId ?: parent?.userId,
                    threadUserLogin = parent?.reply?.threadUserLogin ?: parent?.userLogin,
                    threadUserName = parent?.reply?.threadUserName ?: parent?.userDisplayName,
                )
            },
            type = ChatMessageType.CHAT,
            flags = MessageFlags(),
            outgoingState = OutgoingMessageState.SENDING,
            clientNonce = localMessageId,
        )
    }

    private suspend fun nextLocalMessageId(): String = localIdMutex.withLock {
        localSequence += 1L
        "$LOCAL_ID_PREFIX${currentEpochMillis()}-$localSequence"
    }

    private companion object {
        const val LOCAL_ID_PREFIX = "local-"
        const val MAX_MESSAGE_LENGTH = 500
    }
}

private inline fun <reified T : Throwable> Throwable.findChatMessageCause(): T? {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
