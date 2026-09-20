package io.ferventio.shared.chat

import androidx.compose.runtime.snapshotFlow
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.HighlightAlert
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.history.ChatHistoryPersistenceRuntime
import io.ferventio.shared.history.toChatHistoryConfig
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.Throws
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

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
    val attention: ChatAttentionStateHolder,
    private val historyStore: ChatHistoryStore?,
    private val settings: SharedAppSettingsStateHolder?,
    private val messageRules: SharedMessageRulesStateHolder? = null,
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
    private val onAutoModHeld: (AutoModHeldMessage) -> Unit = {},
) {
    constructor() : this(
        ChatRuntimeStateHolder(),
        ChatAttentionStateHolder(),
        historyStore = null,
        settings = null,
    )

    constructor(state: ChatRuntimeStateHolder) : this(
        state,
        ChatAttentionStateHolder(),
        historyStore = null,
        settings = null,
    )

    constructor(
        state: ChatRuntimeStateHolder,
        attention: ChatAttentionStateHolder,
    ) : this(
        state,
        attention,
        historyStore = null,
        settings = null,
    )

    init {
        require(historyStore == null || settings != null) {
            "Chat history settings are required when a history store is configured"
        }
    }

    private val runGate = ChatSessionRunGate()
    private val badgeClient = TwitchChatBadgeClient()
    private val cheermoteClient = TwitchCheermoteClient()
    private val metadataRefreshGate = ChatMetadataRefreshGate()
    private var runningClient: TwitchEventSubSocketClient? = null
    private var sessionRuntime: TwitchChatSessionRuntime? = null
    private var historyRuntime: ChatHistoryPersistenceRuntime? = null

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
            state.retainChannels(workspace.channelIds)
            attention.retainChannels(workspace.channelIds)
            metadataRefreshGate.retainChannels(workspace.channelIds)

            val sessionSettings = settings
            val sessionHistory = historyStore?.let { store ->
                ChatHistoryPersistenceRuntime(
                    store = store,
                    configProvider = {
                        checkNotNull(sessionSettings).preferences.toChatHistoryConfig()
                    },
                )
            }
            historyRuntime = sessionHistory
            sessionHistory?.restoreRecent(
                state = state,
                channelIds = workspace.channelIds,
            )
            val recentMessagesRuntime = sessionSettings?.let {
                TwitchRecentMessagesRuntime(
                    state = state,
                    history = sessionHistory,
                )
            }

            lateinit var client: TwitchEventSubSocketClient
            val runtime = TwitchChatSessionRuntime(
                authentication = authentication,
                workspace = workspace,
                state = state,
                attention = attention,
                history = sessionHistory,
                messageRules = messageRules,
                onHighlightAlert = onHighlightAlert,
                onAutoModHeld = { message ->
                    if (shouldEmitAutoModAlert(sessionSettings)) {
                        onAutoModHeld(message)
                    }
                },
                onFatalSessionError = { client.close() },
            )
            client = TwitchEventSubSocketClient(
                onStatusChanged = runtime::onConnectionUpdate,
                onSessionReady = runtime::onSessionReady,
                onSessionOpened = runtime::onSessionOpened,
                onEnvelope = { envelope -> runtime.onEnvelope(envelope) },
                onMalformedEnvelope = { _ -> },
                onError = runtime::onSocketError,
            )
            sessionRuntime = runtime
            runningClient = client

            try {
                coroutineScope {
                    val auxiliaryRuntimeJob = launch {
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
                            if (sessionSettings != null && recentMessagesRuntime != null) {
                                launch {
                                    snapshotFlow { sessionSettings.preferences.recentMessagesEnabled }
                                        .distinctUntilChanged()
                                        .collectLatest { enabled ->
                                            if (enabled) {
                                                recentMessagesRuntime.loadChannels(
                                                    workspace.activeRecentMessageChannels(),
                                                )
                                            }
                                        }
                                }
                            }
                            launch {
                                while (isActive) {
                                    delay(AUTOMOD_STALE_SWEEP_INTERVAL_MILLIS)
                                    state.expireStaleAutoModHolds(
                                        olderThanEpochMillis =
                                            Clock.System.now().toEpochMilliseconds() -
                                                AUTOMOD_STALE_HOLD_GRACE_MILLIS,
                                    )
                                }
                            }
                        }
                    }
                    try {
                        client.run()
                    } finally {
                        auxiliaryRuntimeJob.cancelAndJoin()
                    }
                }
            } finally {
                runtime.close()
                client.close()
                sessionHistory?.close()
                withContext(NonCancellable) {
                    sessionHistory?.flushAndClose()
                }
                if (runningClient === client) {
                    runningClient = null
                    sessionRuntime = null
                    historyRuntime = null
                }
                if (state.connectionStatus != ConnectionStatus.FAILED) {
                    state.updateConnection(ConnectionStatus.DISCONNECTED)
                }
            }
        }
    }

    fun close() {
        sessionRuntime?.close()
        historyRuntime?.close()
        runningClient?.close()
    }

    private suspend fun refreshBadgeAssets(
        authentication: StoredAuthentication,
        workspace: WorkspaceRuntimeSnapshot,
    ) {
        if (metadataRefreshGate.shouldRefreshGlobalBadges()) {
            if (
                bestEffort {
                    state.replaceGlobalBadgeAssets(
                        badgeClient.loadGlobal(authentication),
                    )
                }
            ) {
                metadataRefreshGate.markGlobalBadgesLoaded()
            }
        }

        val staleChannels = workspace.channelIds.filter(
            metadataRefreshGate::shouldRefreshChannelBadges,
        )
        forEachChannelMetadataBatch(staleChannels) { channelId ->
            channelId to bestEffort {
                state.replaceChannelBadgeAssets(
                    channelId = channelId,
                    value = badgeClient.loadChannel(
                        authentication = authentication,
                        broadcasterId = channelId,
                    ),
                )
            }
        }.filter { (_, loaded) -> loaded }
            .forEach { (channelId, _) ->
                metadataRefreshGate.markChannelBadgesLoaded(channelId)
            }
    }

    private suspend fun refreshCheermoteAssets(
        authentication: StoredAuthentication,
        workspace: WorkspaceRuntimeSnapshot,
    ) {
        val staleChannels = workspace.channelIds.filter(
            metadataRefreshGate::shouldRefreshCheermotes,
        )
        forEachChannelMetadataBatch(staleChannels) { channelId ->
            channelId to bestEffort {
                state.replaceChannelCheermoteAssets(
                    channelId = channelId,
                    value = cheermoteClient.load(
                        authentication = authentication,
                        broadcasterId = channelId,
                    ),
                )
            }
        }.filter { (_, loaded) -> loaded }
            .forEach { (channelId, _) ->
                metadataRefreshGate.markCheermotesLoaded(channelId)
            }
    }

    private suspend fun <T> forEachChannelMetadataBatch(
        channelIds: Iterable<String>,
        block: suspend (String) -> T,
    ): List<T> {
        val normalized = channelIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        return buildList {
            for (batch in normalized.chunked(METADATA_REFRESH_CONCURRENCY)) {
                addAll(
                    coroutineScope {
                        batch.map { channelId ->
                            async { block(channelId) }
                        }.awaitAll()
                    },
                )
            }
        }
    }

    private suspend fun bestEffort(block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Presentation metadata is optional; live chat must continue without it.
            false
        }
}

private const val METADATA_REFRESH_CONCURRENCY = 4
private const val AUTOMOD_STALE_SWEEP_INTERVAL_MILLIS = 30_000L
private const val AUTOMOD_STALE_HOLD_GRACE_MILLIS = 10 * 60 * 1_000L

internal fun shouldEmitAutoModAlert(settings: SharedAppSettingsStateHolder?): Boolean =
    settings?.preferences?.autoModNotificationsEnabled != false
