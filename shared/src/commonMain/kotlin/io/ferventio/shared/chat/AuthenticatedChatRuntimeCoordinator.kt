package io.ferventio.shared.chat

import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.Throws
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

internal class ChatSessionRunGate {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T {
        mutex.lock()
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}

/**
 * Public KMP entry point for authenticated Twitch chat transport.
 *
 * The coordinator owns the socket lifecycle while writing all observable state into the supplied
 * [ChatRuntimeStateHolder], allowing Compose Android/iOS surfaces to share one chat state graph.
 */
class AuthenticatedChatRuntimeCoordinator(
    val state: ChatRuntimeStateHolder,
) {
    constructor() : this(ChatRuntimeStateHolder())

    private val runGate = ChatSessionRunGate()
    private val badgeClient = TwitchChatBadgeClient()
    private val cheermoteClient = TwitchCheermoteClient()
    private var runningClient: TwitchEventSubSocketClient? = null
    private var sessionRuntime: TwitchChatSessionRuntime? = null

    @Throws(Exception::class)
    suspend fun run(
        authentication: StoredAuthentication,
        workspace: WorkspaceRuntimeSnapshot,
    ) {
        runGate.run {
            require(workspace.channels.isNotEmpty()) {
                "Authenticated chat runtime requires at least one workspace channel"
            }
            state.clearAuthenticationRequired()

            lateinit var client: TwitchEventSubSocketClient
            val runtime = TwitchChatSessionRuntime(
                authentication = authentication,
                workspace = workspace,
                state = state,
                onFatalSessionError = { client.close() },
            )
            client = TwitchEventSubSocketClient(
                onStatusChanged = runtime::onConnectionUpdate,
                onSessionReady = runtime::onSessionReady,
                onEnvelope = { envelope -> runtime.onEnvelope(envelope) },
                onMalformedEnvelope = { _ -> },
                onError = runtime::onSocketError,
            )
            sessionRuntime = runtime
            runningClient = client
            state.retainChannels(workspace.channelIds)

            try {
                coroutineScope {
                    val presentationAssetsJob = launch {
                        coroutineScope {
                            launch {
                                refreshBadgeAssets(
                                    authentication = authentication,
                                    workspace = workspace,
                                )
                            }
                            launch {
                                refreshCheermoteAssets(
                                    authentication = authentication,
                                    workspace = workspace,
                                )
                            }
                        }
                    }
                    try {
                        client.run()
                    } finally {
                        presentationAssetsJob.cancelAndJoin()
                    }
                }
            } finally {
                runtime.close()
                client.close()
                if (runningClient === client) {
                    runningClient = null
                    sessionRuntime = null
                }
                if (state.connectionStatus != ConnectionStatus.FAILED) {
                    state.updateConnection(ConnectionStatus.DISCONNECTED)
                }
            }
        }
    }

    fun close() {
        sessionRuntime?.close()
        runningClient?.close()
    }

    private suspend fun refreshBadgeAssets(
        authentication: StoredAuthentication,
        workspace: WorkspaceRuntimeSnapshot,
    ) {
        bestEffort {
            state.replaceGlobalBadgeAssets(
                badgeClient.loadGlobal(authentication),
            )
        }
        workspace.channelIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach { channelId ->
                bestEffort {
                    state.replaceChannelBadgeAssets(
                        channelId = channelId,
                        value = badgeClient.loadChannel(
                            authentication = authentication,
                            broadcasterId = channelId,
                        ),
                    )
                }
            }
    }

    private suspend fun refreshCheermoteAssets(
        authentication: StoredAuthentication,
        workspace: WorkspaceRuntimeSnapshot,
    ) {
        workspace.channelIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach { channelId ->
                bestEffort {
                    state.replaceChannelCheermoteAssets(
                        channelId = channelId,
                        value = cheermoteClient.load(
                            authentication = authentication,
                            broadcasterId = channelId,
                        ),
                    )
                }
            }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Presentation metadata is optional; live chat must continue without it.
        }
    }
}
