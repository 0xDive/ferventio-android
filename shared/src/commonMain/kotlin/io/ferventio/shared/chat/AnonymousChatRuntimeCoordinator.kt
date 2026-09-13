package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.HighlightAlert
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.ModerationAction
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlin.Throws

/**
 * Shared signed-out chat runtime. Twitch IRC owns transport only; this coordinator applies the
 * resulting domain events to the same Compose-facing chat/attention state used by EventSub.
 */
class AnonymousChatRuntimeCoordinator(
    val state: ChatRuntimeStateHolder,
    val attention: ChatAttentionStateHolder,
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

    private val runGate = ChatSessionRunGate()
    private var runningClient: TwitchAnonymousChatSocketClient? = null

    @Throws(Exception::class)
    suspend fun run(workspace: WorkspaceRuntimeStateHolder) {
        runGate.run {
            require(workspace.channels.isNotEmpty()) {
                "Anonymous chat runtime requires at least one workspace channel"
            }
            state.clearAuthenticationRequired()
            state.retainChannels(workspace.channelIds)
            attention.retainChannels(workspace.channelIds)

            val runtime = AnonymousChatSessionRuntime(
                state = state,
                attention = attention,
                workspace = workspace,
                messageRules = messageRules,
                onHighlightAlert = onHighlightAlert,
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

            try {
                client.run()
            } finally {
                client.close()
                if (runningClient === client) runningClient = null
                if (state.connectionStatus != ConnectionStatus.FAILED) {
                    state.updateConnection(ConnectionStatus.DISCONNECTED)
                }
            }
        }
    }

    fun close() {
        runningClient?.close()
    }
}

/** Reducer for the ChatEvent subset emitted by anonymous Twitch IRC. */
internal class AnonymousChatSessionRuntime(
    private val state: ChatRuntimeStateHolder,
    private val attention: ChatAttentionStateHolder,
    private val workspace: WorkspaceRuntimeStateHolder,
    private val messageRules: SharedMessageRulesStateHolder? = null,
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
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
            true
        }

        is ChatEvent.MessageDeleted -> {
            state.markMessageDeleted(event.channelId, event.messageId)
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
            true
        }

        is ChatEvent.ChatCleared -> {
            state.clearChannelMessages(event.channelId)
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
