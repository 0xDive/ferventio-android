package io.ferventio.shared.chat

import androidx.compose.runtime.snapshotFlow
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.HighlightAlert
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.ModerationAction
import io.ferventio.shared.history.ChatHistoryPersistenceRuntime
import io.ferventio.shared.history.toChatHistoryConfig
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlin.Throws
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared signed-out chat runtime. Twitch IRC owns transport only; this coordinator applies the
 * resulting domain events to the same Compose-facing chat/attention state used by EventSub.
 */
class AnonymousChatRuntimeCoordinator(
    val state: ChatRuntimeStateHolder,
    val attention: ChatAttentionStateHolder,
    private val historyStore: ChatHistoryStore? = null,
    private val settings: SharedAppSettingsStateHolder? = null,
    private val messageRules: SharedMessageRulesStateHolder? = null,
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
) {
    constructor() : this(
        state = ChatRuntimeStateHolder(),
        attention = ChatAttentionStateHolder(),
    )

    constructor(state: ChatRuntimeStateHolder) : this(
        state = state,
        attention = ChatAttentionStateHolder(),
    )

    init {
        require(historyStore == null || settings != null) {
            "Chat history settings are required when a history store is configured"
        }
    }

    private val runGate = ChatSessionRunGate()
    private val badgeRelayClient = FerventioBadgeRelayClient()
    private var runningClient: TwitchAnonymousChatSocketClient? = null
    private var historyRuntime: ChatHistoryPersistenceRuntime? = null

    @Throws(Exception::class)
    suspend fun run(
        workspace: WorkspaceRuntimeStateHolder,
        metadataServerUrl: String? = null,
    ) {
        runGate.run {
            require(workspace.channels.isNotEmpty()) {
                "Anonymous chat runtime requires at least one workspace channel"
            }
            state.clearAuthenticationRequired()
            state.retainChannels(workspace.channelIds)
            attention.retainChannels(workspace.channelIds)

            val sessionSettings = settings
            val badgeServerUrl = metadataServerUrl?.trim()?.takeIf(String::isNotEmpty)
            // Authenticated and anonymous transports can briefly overlap while one cancelled KMP
            // task drains history writes. History is optional, so a busy bound store must never
            // prevent read-only IRC from starting.
            val sessionHistory = historyStore?.let { store ->
                runCatching {
                    ChatHistoryPersistenceRuntime(
                        store = store,
                        configProvider = {
                            checkNotNull(sessionSettings).preferences.toChatHistoryConfig()
                        },
                    )
                }.getOrNull()
            }
            historyRuntime = sessionHistory
            val recentMessagesRuntime = sessionSettings?.let {
                TwitchRecentMessagesRuntime(
                    state = state,
                    history = sessionHistory,
                )
            }
            val recentMessagesAttemptPolicy = AnonymousRecentMessagesAttemptPolicy()

            try {
                coroutineScope {
                    val runtime = AnonymousChatSessionRuntime(
                        state = state,
                        attention = attention,
                        workspace = workspace,
                        messageRules = messageRules,
                        onHighlightAlert = onHighlightAlert,
                        persistMessage = { message -> sessionHistory?.saveMessage(message) },
                        persistMessageDeleted = { channelId, messageId ->
                            sessionHistory?.markMessageDeleted(channelId, messageId)
                        },
                        persistUserMessagesDeleted = { channelId, userId ->
                            sessionHistory?.markUserMessagesDeleted(channelId, userId)
                        },
                        persistChannelCleared = { channelId ->
                            sessionHistory?.clearChannel(channelId)
                        },
                        onCanonicalChannelResolved = { channel ->
                            launch {
                                sessionHistory?.restoreRecent(
                                    state = state,
                                    channelIds = listOf(channel.id),
                                )
                            }
                            badgeServerUrl?.let { serverUrl ->
                                launch {
                                    refreshChannelBadgeAssets(
                                        serverUrl = serverUrl,
                                        channelId = channel.id,
                                    )
                                }
                            }
                        },
                    )
                    val client = TwitchAnonymousChatSocketClient(
                        channels = workspace.channels,
                        onStatusChanged = runtime::onConnectionUpdate,
                        onEvent = { event -> runtime.onEvent(event) },
                        onRoomResolved = runtime::onRoomResolved,
                        onNotice = runtime::onNotice,
                        onError = runtime::onSocketError,
                    )
                    runningClient = client
                    val badgeMetadataJob = badgeServerUrl?.let { serverUrl ->
                        launch {
                            coroutineScope {
                                launch {
                                    refreshGlobalBadgeAssets(serverUrl)
                                }
                                workspace.channelIds
                                    .asSequence()
                                    .map(String::trim)
                                    .filter(String::isCanonicalAnonymousChannelId)
                                    .distinct()
                                    .forEach { channelId ->
                                        launch {
                                            refreshChannelBadgeAssets(
                                                serverUrl = serverUrl,
                                                channelId = channelId,
                                            )
                                        }
                                    }
                            }
                        }
                    }
                    val recentMessagesJob = if (
                        sessionSettings != null && recentMessagesRuntime != null
                    ) {
                        launch {
                            snapshotFlow {
                                workspace.anonymousRecentMessagesTarget(
                                    enabled = sessionSettings.preferences.recentMessagesEnabled,
                                )
                            }
                                .distinctUntilChanged()
                                .collectLatest { target ->
                                    val channel = target.channel ?: return@collectLatest
                                    if (!target.enabled) return@collectLatest
                                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                                    if (!recentMessagesAttemptPolicy.shouldAttempt(channel, nowMillis)) {
                                        return@collectLatest
                                    }
                                    recentMessagesRuntime.loadChannels(listOf(channel))
                                }
                        }
                    } else {
                        null
                    }

                    try {
                        client.run()
                    } finally {
                        recentMessagesJob?.cancelAndJoin()
                        badgeMetadataJob?.cancelAndJoin()
                        client.close()
                        if (runningClient === client) runningClient = null
                    }
                }
            } finally {
                sessionHistory?.close()
                withContext(NonCancellable) {
                    sessionHistory?.flushAndClose()
                }
                if (historyRuntime === sessionHistory) historyRuntime = null
                if (state.connectionStatus != ConnectionStatus.FAILED) {
                    state.updateConnection(ConnectionStatus.DISCONNECTED)
                }
            }
        }
    }

    fun close() {
        historyRuntime?.close()
        runningClient?.close()
    }

    private suspend fun refreshGlobalBadgeAssets(serverUrl: String) {
        bestEffort {
            state.replaceGlobalBadgeAssets(
                badgeRelayClient.loadGlobal(serverUrl),
            )
        }
    }

    private suspend fun refreshChannelBadgeAssets(
        serverUrl: String,
        channelId: String,
    ) {
        bestEffort {
            state.replaceChannelBadgeAssets(
                channelId = channelId,
                value = badgeRelayClient.loadChannel(
                    serverUrl = serverUrl,
                    broadcasterId = channelId,
                ),
            )
        }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Presentation metadata is optional; read-only IRC must continue without it.
        }
    }
}

internal data class AnonymousRecentMessagesTarget(
    val enabled: Boolean,
    val channel: ChatChannel?,
)

internal fun WorkspaceRuntimeStateHolder.anonymousRecentMessagesTarget(
    enabled: Boolean,
): AnonymousRecentMessagesTarget {
    val selected = selectedChannelId?.let { selectedId ->
        channels.firstOrNull { channel ->
            channel.id == selectedId && channel.id.isCanonicalAnonymousChannelId()
        }
    }
    return AnonymousRecentMessagesTarget(
        enabled = enabled,
        channel = selected,
    )
}

internal class AnonymousRecentMessagesAttemptPolicy(
    private val cooldownMillis: Long = RETRY_COOLDOWN_MILLIS,
) {
    private val lastAttemptByLogin = mutableMapOf<String, Long>()

    fun shouldAttempt(channel: ChatChannel, nowMillis: Long): Boolean {
        val login = channel.login.trim().lowercase().takeIf(String::isNotEmpty) ?: return false
        val lastAttempt = lastAttemptByLogin[login]
        if (lastAttempt != null && nowMillis - lastAttempt < cooldownMillis) return false
        lastAttemptByLogin[login] = nowMillis
        return true
    }

    private companion object {
        const val RETRY_COOLDOWN_MILLIS = 60_000L
    }
}

/** Reducer for the ChatEvent subset emitted by anonymous Twitch IRC. */
internal class AnonymousChatSessionRuntime(
    private val state: ChatRuntimeStateHolder,
    private val attention: ChatAttentionStateHolder,
    private val workspace: WorkspaceRuntimeStateHolder,
    private val messageRules: SharedMessageRulesStateHolder? = null,
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
    private val persistMessage: (ChatMessage) -> Unit = {},
    private val persistMessageDeleted: (channelId: String, messageId: String) -> Unit = { _, _ -> },
    private val persistUserMessagesDeleted: (channelId: String, userId: String) -> Unit = { _, _ -> },
    private val persistChannelCleared: (channelId: String) -> Unit = {},
    private val onCanonicalChannelResolved: (ChatChannel) -> Unit = {},
) {
    private var evaluatorRules = messageRules?.snapshot ?: SharedMessageRulesSnapshot()
    private var messageRuleEvaluator = compileEvaluator(evaluatorRules)

    fun onEvent(event: ChatEvent): Boolean = when (event) {
        is ChatEvent.Message -> {
            val message = event.message
            val evaluator = currentMessageRuleEvaluator()
            val decoration = evaluator.evaluate(message)
            messageRules?.recordDecoration(message.id, decoration)
            state.append(message)
            attention.recordIncoming(
                message = message,
                session = null,
                decoration = decoration,
                directMention = evaluator.isDirectMention(message),
            )
            if (
                decoration.isHighlighted &&
                !decoration.isIgnored &&
                (decoration.playSound || decoration.push)
            ) {
                onHighlightAlert(
                    HighlightAlert(
                        message = message,
                        reasons = decoration.highlightReasons,
                        playSound = decoration.playSound,
                        push = decoration.push,
                    ),
                )
            }
            if (message.channelId.isCanonicalAnonymousChannelId()) {
                persistMessage(message)
            }
            true
        }

        is ChatEvent.MessageDeleted -> {
            state.markMessageDeleted(event.channelId, event.messageId)
            if (event.channelId.isCanonicalAnonymousChannelId()) {
                persistMessageDeleted(event.channelId, event.messageId)
            }
            true
        }

        is ChatEvent.UserMessagesCleared -> {
            state.markUserMessagesDeleted(
                channelId = event.channelId,
                userId = event.userId,
                action = if (event.isPermanent == true) {
                    ModerationAction.BAN
                } else {
                    ModerationAction.TIMEOUT
                },
            )
            if (event.channelId.isCanonicalAnonymousChannelId()) {
                persistUserMessagesDeleted(event.channelId, event.userId)
            }
            true
        }

        is ChatEvent.ChatCleared -> {
            state.clearChannelMessages(event.channelId)
            if (event.channelId.isCanonicalAnonymousChannelId()) {
                persistChannelCleared(event.channelId)
            }
            true
        }

        is ChatEvent.AutoModHeld,
        is ChatEvent.AutoModUpdated,
        is ChatEvent.ModerationPerformed,
        is ChatEvent.ChatSettingsUpdated,
        -> false
    }

    fun onRoomResolved(channelLogin: String, roomId: String) {
        val channel = workspace.channels.firstOrNull { candidate ->
            candidate.login.equals(channelLogin.trim(), ignoreCase = true)
        } ?: return
        val oldId = channel.id
        val normalizedRoomId = roomId.trim().takeIf(String::isNotEmpty) ?: return
        if (!workspace.remapChannelId(oldId, normalizedRoomId)) return

        state.remapAnonymousChannelId(
            channelId = oldId,
            replacementId = normalizedRoomId,
            replacementLogin = channel.login,
        )
        attention.remapChannelId(oldId, normalizedRoomId)
        workspace.channels.firstOrNull { candidate -> candidate.id == normalizedRoomId }
            ?.let(onCanonicalChannelResolved)
    }

    fun onConnectionUpdate(update: TwitchAnonymousChatConnectionUpdate) {
        state.updateConnection(
            status = update.status,
            attempt = update.attempt,
            errorMessage = update.error,
        )
    }

    fun onNotice(notice: String) {
        val snapshot = state.snapshot
        state.updateConnection(
            status = snapshot.connectionStatus,
            detail = notice.trim().take(300).takeIf(String::isNotEmpty),
            attempt = snapshot.connectionAttempt,
            errorMessage = snapshot.connectionErrorMessage,
        )
    }

    fun onSocketError(error: Throwable) {
        val snapshot = state.snapshot
        state.updateConnection(
            status = snapshot.connectionStatus,
            detail = snapshot.connectionDetail,
            attempt = snapshot.connectionAttempt,
            errorMessage = error.message ?: "Anonymous IRC connection failed",
        )
    }

    private fun currentMessageRuleEvaluator(): MessageRuleEvaluator {
        val currentRules = messageRules?.snapshot ?: SharedMessageRulesSnapshot()
        if (currentRules != evaluatorRules) {
            evaluatorRules = currentRules
            messageRuleEvaluator = compileEvaluator(currentRules)
        }
        return messageRuleEvaluator
    }

    private fun compileEvaluator(rules: SharedMessageRulesSnapshot): MessageRuleEvaluator =
        MessageRuleEvaluator.compile(
            highlights = rules.highlightRules,
            ignores = rules.ignoreRules,
            session = null,
        )
}

private fun String.isCanonicalAnonymousChannelId(): Boolean {
    val normalized = trim()
    return normalized.isNotEmpty() && !normalized.startsWith("anonymous:")
}

/** Android-parity room-id migration for the state anonymous IRC can populate before resolution. */
private fun ChatRuntimeStateHolder.remapAnonymousChannelId(
    channelId: String,
    replacementId: String,
    replacementLogin: String,
) {
    val currentId = channelId.trim()
    val nextId = replacementId.trim()
    if (currentId.isEmpty() || nextId.isEmpty() || currentId == nextId) return

    val liveMessages = messagesByChannel[currentId].orEmpty().map { message ->
        message.copy(
            channelId = nextId,
            channelLogin = replacementLogin,
        )
    }
    val badges = badgeAssetsByChannel[currentId].orEmpty()
    val cheermotes = cheermoteAssetsByChannel[currentId].orEmpty()

    if (liveMessages.isNotEmpty()) {
        replaceChannelMessages(nextId, liveMessages)
    }
    if (badges.isNotEmpty()) {
        replaceChannelBadgeAssets(nextId, badges)
    }
    if (cheermotes.isNotEmpty()) {
        replaceChannelCheermoteAssets(nextId, cheermotes)
    }
    removeChannel(currentId)
}
