package io.ferventio.shared.chat

import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.Throws
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

            val runtime = TwitchChatSessionRuntime(
                authentication = authentication,
                workspace = workspace,
                state = state,
            )
            val client = TwitchEventSubSocketClient(
                onStatusChanged = runtime::onConnectionUpdate,
                onSessionReady = runtime::onSessionReady,
                onEnvelope = { envelope -> runtime.onEnvelope(envelope) },
                onMalformedEnvelope = { _ -> },
                onError = runtime::onSocketError,
            )
            sessionRuntime = runtime
            runningClient = client

            try {
                client.run()
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
}
