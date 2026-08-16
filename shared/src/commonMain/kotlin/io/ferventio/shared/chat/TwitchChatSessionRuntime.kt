package io.ferventio.shared.chat

import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * Binds EventSub lifecycle callbacks to the shared chat state without owning platform UI.
 * Socket transport remains separate so this session behavior is deterministic in common tests.
 */
internal class TwitchChatSessionRuntime(
    private val authentication: StoredAuthentication,
    private val workspace: WorkspaceRuntimeSnapshot,
    private val state: ChatRuntimeStateHolder,
    private val bootstrapCoordinator: TwitchEventSubBootstrapCoordinator =
        TwitchEventSubBootstrapCoordinator(),
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
            bootstrapCoordinator.createRemaining(
                authentication = authentication,
                sessionId = sessionId,
                bootstrap = bootstrap,
            )
        }
        return bootstrap.subscriptionCount
    }

    fun onEnvelope(envelope: TwitchEventSubProtocolEnvelope): Boolean {
        val message = runCatching {
            TwitchChatMessageEventParser.parse(envelope)
        }.getOrNull() ?: return false
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
}
