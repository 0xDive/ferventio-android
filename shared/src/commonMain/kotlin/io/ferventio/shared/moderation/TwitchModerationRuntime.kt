package io.ferventio.shared.moderation

import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import kotlin.time.Clock

/**
 * Executes Twitch moderation mutations and updates shared chat state only after Helix accepts them.
 * EventSub may later replay the same mutation; the state updates are intentionally idempotent.
 */
class TwitchModerationRuntime(
    private val chatState: ChatRuntimeStateHolder,
    private val gateway: TwitchModerationGateway = TwitchModerationClient(),
    private val currentEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun deleteChatMessage(
        authentication: StoredAuthentication,
        broadcasterId: String,
        messageId: String,
    ): Boolean {
        gateway.deleteChatMessage(authentication, broadcasterId, messageId)
        return chatState.markMessageDeleted(
            channelId = broadcasterId,
            messageId = messageId,
            atMillis = currentEpochMillis(),
        )
    }

    suspend fun timeoutUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        durationSeconds: Int,
        reason: String? = null,
    ): Int {
        gateway.timeoutUser(
            authentication = authentication,
            broadcasterId = broadcasterId,
            targetUserId = targetUserId,
            durationSeconds = durationSeconds,
            reason = reason,
        )
        return chatState.markUserMessagesDeleted(
            channelId = broadcasterId,
            userId = targetUserId,
            atMillis = currentEpochMillis(),
            action = ModerationAction.TIMEOUT,
        )
    }

    suspend fun banUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        reason: String? = null,
    ): Int {
        gateway.banUser(
            authentication = authentication,
            broadcasterId = broadcasterId,
            targetUserId = targetUserId,
            reason = reason,
        )
        return chatState.markUserMessagesDeleted(
            channelId = broadcasterId,
            userId = targetUserId,
            atMillis = currentEpochMillis(),
            action = ModerationAction.BAN,
        )
    }

    suspend fun unbanUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
    ) {
        gateway.unbanUser(authentication, broadcasterId, targetUserId)
    }

    suspend fun clearChatMessages(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ): Boolean {
        gateway.clearChatMessages(authentication, broadcasterId)
        return chatState.clearChannelMessages(broadcasterId)
    }
}
