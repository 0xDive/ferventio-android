package io.ferventio.shared.chat

import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** Binds EventSub lifecycle callbacks to shared chat state without owning platform UI. */
internal class TwitchChatSessionRuntime(
    private val authentication: StoredAuthentication,
    private val workspace: WorkspaceRuntimeSnapshot,
    private val state: ChatRuntimeStateHolder,
    private val bootstrapCoordinator: TwitchEventSubBootstrapCoordinator = TwitchEventSubBootstrapCoordinator(),
) {
    private var supplementalSubscriptionsJob: Job? = null

    suspend fun onSessionReady(sessionId: String): Int {
        supplementalSubscriptionsJob?.cancel()
        state.retainChannels(workspace.channelIds)
        val bootstrap = bootstrapCoordinator.bootstrap(
            authentication = authentication,
            sessionId = sessionId,
            channels = workspace.channels,
            moderatedChannelIds = workspace.moderatorChannelIds,
        )
        supplementalSubscriptionsJob = CoroutineScope(currentCoroutineContext()).launch {
            val failures = bootstrapCoordinator.createRemaining(
                authentication = authentication,
                sessionId = sessionId,
                bootstrap = bootstrap,
            )
            reportSupplementalSubscriptionFailures(failures)
        }
        return bootstrap.subscriptionCount
    }

    fun onEnvelope(envelope: TwitchEventSubProtocolEnvelope): Boolean {
        if (
            envelope.type == "revocation" &&
            TwitchEventSubConnectionPolicy.shouldStopAfterRevocation(envelope.revocationStatus)
        ) {
            onSocketError(
                TwitchEventSubAuthorizationRevokedException(envelope.subscriptionType),
            )
            return true
        }

        val mutation = runCatching { TwitchChatMutationEventParser.parse(envelope) }.getOrNull()
        if (mutation != null) {
            when (mutation) {
                is TwitchChatMutationEvent.MessageDeleted -> state.markMessageDeleted(
                    channelId = mutation.channelId,
                    messageId = mutation.messageId,
                )
                is TwitchChatMutationEvent.UserMessagesCleared -> state.markUserMessagesDeleted(
                    channelId = mutation.channelId,
                    userId = mutation.userId,
                )
                is TwitchChatMutationEvent.ChatCleared -> state.clearChannelMessages(mutation.channelId)
            }
            return true
        }

        val interactive = runCatching { TwitchInteractiveEventParser.parse(envelope) }.getOrNull()
        if (interactive != null) {
            state.applyInteractive(interactive)
            return true
        }

        val message = runCatching { TwitchChatMessageEventParser.parse(envelope) }.getOrNull()
            ?: return false
        state.append(message)
        return true
    }

    fun onConnectionUpdate(update: TwitchEventSubConnectionUpdate) {
        state.updateConnection(
            status = update.status,
            attempt = update.attempt,
            errorMessage = update.error,
        )
    }

    fun onSocketError(error: Throwable) {
        if (error.isTwitchAuthenticationFailure()) {
            state.markAuthenticationRequired(error.message)
            return
        }
        val snapshot = state.snapshot
        state.updateConnection(
            status = snapshot.connectionStatus,
            detail = snapshot.connectionDetail,
            attempt = snapshot.connectionAttempt,
            errorMessage = error.message ?: "EventSub connection failed",
        )
    }

    fun close() {
        supplementalSubscriptionsJob?.cancel()
        supplementalSubscriptionsJob = null
    }

    private fun reportSupplementalSubscriptionFailures(
        failures: List<TwitchEventSubBootstrapFailure>,
    ) {
        if (failures.isEmpty()) return
        val authenticationFailure = failures
            .asSequence()
            .mapNotNull(TwitchEventSubBootstrapFailure::cause)
            .firstOrNull(Throwable::isTwitchAuthenticationFailure)
        if (authenticationFailure != null) {
            onSocketError(authenticationFailure)
            return
        }

        val first = failures.first()
        onSocketError(
            IllegalStateException(
                "EventSub ${first.type} subscription failed for ${first.channel.login}: ${first.message}",
                first.cause,
            ),
        )
    }
}
