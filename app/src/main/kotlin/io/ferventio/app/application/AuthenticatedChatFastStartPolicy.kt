package io.ferventio.app.application

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.shared.chat.AuthenticatedChatFastStartAttemptTracker as SharedAuthenticatedChatFastStartAttemptTracker
import io.ferventio.shared.chat.AuthenticatedChatFastStartPolicy as SharedAuthenticatedChatFastStartPolicy

/** Compatibility adapter while the legacy Android controller still lives in the app module. */
object AuthenticatedChatFastStartPolicy {
    fun candidateKey(
        isAuthenticated: Boolean,
        isBootstrapping: Boolean,
        isChannelsLoading: Boolean,
        connectionStatus: ConnectionStatus,
        userId: String?,
        channels: List<ChatChannel>,
    ): String? = SharedAuthenticatedChatFastStartPolicy.candidateKey(
        isAuthenticated = isAuthenticated,
        isBootstrapping = isBootstrapping,
        isChannelsLoading = isChannelsLoading,
        connectionStatus = connectionStatus,
        userId = userId,
        channels = channels,
    )
}

/** Compatibility adapter for the Android controller's existing one-shot state holder. */
internal class AuthenticatedChatFastStartAttemptTracker {
    private val delegate = SharedAuthenticatedChatFastStartAttemptTracker()

    fun consumeCandidate(
        isAuthenticated: Boolean,
        isChannelsLoading: Boolean,
        candidateKey: String?,
    ): String? = delegate.consumeCandidate(
        isAuthenticated = isAuthenticated,
        isChannelsLoading = isChannelsLoading,
        candidateKey = candidateKey,
    )
}
