package io.ferventio.shared.moderation

import io.ferventio.app.domain.AutoModMessageStatus
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
    ): Boolean = executeMutation {
        gateway.deleteChatMessage(authentication, broadcasterId, messageId)
        chatState.markMessageDeleted(
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
    ): Int = executeMutation {
        gateway.timeoutUser(
            authentication = authentication,
            broadcasterId = broadcasterId,
            targetUserId = targetUserId,
            durationSeconds = durationSeconds,
            reason = reason,
        )
        chatState.markUserMessagesDeleted(
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
    ): Int = executeMutation {
        gateway.banUser(
            authentication = authentication,
            broadcasterId = broadcasterId,
            targetUserId = targetUserId,
            reason = reason,
        )
        chatState.markUserMessagesDeleted(
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
    ) = executeMutation {
        gateway.unbanUser(authentication, broadcasterId, targetUserId)
    }

    suspend fun warnUser(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
        reason: String,
    ) = executeMutation {
        gateway.warnUser(
            authentication = authentication,
            broadcasterId = broadcasterId,
            targetUserId = targetUserId,
            reason = reason,
        )
    }

    suspend fun clearChatMessages(
        authentication: StoredAuthentication,
        broadcasterId: String,
    ): Boolean = executeMutation {
        gateway.clearChatMessages(authentication, broadcasterId)
        chatState.clearChannelMessages(broadcasterId)
    }

    suspend fun decideAutoModMessage(
        authentication: StoredAuthentication,
        messageId: String,
        approve: Boolean,
    ): Boolean = try {
        executeMutation {
            gateway.decideAutoModMessage(
                authentication = authentication,
                messageId = messageId,
                approve = approve,
            )
            val session = authentication.accessLease?.session
            chatState.markAutoModDecision(
                messageId = messageId,
                status = if (approve) AutoModMessageStatus.APPROVED else AutoModMessageStatus.DENIED,
                moderatorId = session?.userId,
                moderatorLogin = session?.login,
                moderatorName = session?.login,
            )
        }
    } catch (error: TwitchModerationMutationException) {
        if (error.statusCode != AUTOMOD_MESSAGE_NOT_FOUND_CODE) throw error
        chatState.markAutoModDecision(
            messageId = messageId,
            status = AutoModMessageStatus.EXPIRED,
        )
        false
    }

    private suspend fun <T> executeMutation(block: suspend () -> T): T = try {
        block()
    } catch (error: TwitchModerationMutationException) {
        // Twitch uses 401 for invalid/mismatched tokens and missing scopes. A 403 means the
        // authenticated user lacks moderator permission in this broadcaster's channel, so
        // reauthorizing the same account is not a useful recovery action.
        if (error.statusCode == AUTHENTICATION_FAILURE_CODE) {
            chatState.markAuthenticationRequired(error.message)
        }
        throw error
    }

    private companion object {
        const val AUTHENTICATION_FAILURE_CODE = 401
        const val AUTOMOD_MESSAGE_NOT_FOUND_CODE = 404
    }
}
