package io.ferventio.app.application

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus

/**
 * Detects the short bootstrap window where authenticated state and persisted Twitch
 * channel IDs are already available while Helix is still refreshing channel metadata.
 * EventSub can safely start from this snapshot instead of waiting for that refresh.
 */
object AuthenticatedChatFastStartPolicy {
    private const val ANONYMOUS_CHANNEL_ID_PREFIX = "irc:"

    fun candidateKey(
        isAuthenticated: Boolean,
        isBootstrapping: Boolean,
        isChannelsLoading: Boolean,
        connectionStatus: ConnectionStatus,
        userId: String?,
        channels: List<ChatChannel>,
    ): String? {
        if (!isAuthenticated || isBootstrapping || !isChannelsLoading) return null
        if (connectionStatus != ConnectionStatus.DISCONNECTED) return null
        val normalizedUserId = userId?.trim().orEmpty()
        if (normalizedUserId.isBlank() || channels.isEmpty()) return null
        if (channels.any { channel ->
                channel.id.isBlank() || channel.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX)
            }
        ) {
            return null
        }
        val channelIds = channels.map(ChatChannel::id).distinct().sorted()
        return buildString {
            append(normalizedUserId)
            append(':')
            append(channelIds.joinToString(","))
        }
    }
}

/**
 * Keeps cached-channel fast start one-shot within a single authenticated channel-loading window.
 * Connection-status changes may temporarily make [AuthenticatedChatFastStartPolicy.candidateKey]
 * null; they must not make the same snapshot eligible for another forced reconnect.
 */
internal class AuthenticatedChatFastStartAttemptTracker {
    private var attemptedKey: String? = null

    fun consumeCandidate(
        isAuthenticated: Boolean,
        isChannelsLoading: Boolean,
        candidateKey: String?,
    ): String? {
        if (!isAuthenticated || !isChannelsLoading) {
            attemptedKey = null
            return null
        }
        if (candidateKey == null || candidateKey == attemptedKey) return null
        attemptedKey = candidateKey
        return candidateKey
    }
}
