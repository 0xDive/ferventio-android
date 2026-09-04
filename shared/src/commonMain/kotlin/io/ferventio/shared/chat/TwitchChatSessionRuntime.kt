package io.ferventio.shared.chat

import io.ferventio.app.domain.HighlightAlert
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.history.ChatHistoryPersistenceRuntime
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** Binds EventSub lifecycle callbacks to shared chat and attention state without owning platform UI. */
internal class TwitchChatSessionRuntime(
    private val authentication: StoredAuthentication,
    private val workspace: WorkspaceRuntimeSnapshot,
    private val state: ChatRuntimeStateHolder,
    private val attention: ChatAttentionStateHolder = ChatAttentionStateHolder(),
    private val history: ChatHistoryPersistenceRuntime? = null,
    private val messageRules: SharedMessageRulesStateHolder? = null,
    private val bootstrapCoordinator: TwitchEventSubBootstrapCoordinator = TwitchEventSubBootstrapCoordinator(),
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
    private val onFatalSessionError: (Throwable) -> Unit = {},
) {
    private var supplementalSubscriptionsJob: Job? = null
    private val session = authentication.accessLease?.session
    private var evaluatorRules = messageRules?.snapshot ?: SharedMessageRulesSnapshot()
    private var messageRuleEvaluator = compileEvaluator(evaluatorRules)

    suspend fun onSessionReady(sessionId: String): Int {
        supplementalSubscriptionsJob?.cancel()
        state.retainChannels(workspace.channelIds)
        attention.retainChannels(workspace.channelIds)
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
                is TwitchChatMutationEvent.MessageDeleted -> {
                    state.markMessageDeleted(
                        channelId = mutation.channelId,
                        messageId = mutation.messageId,
                    )
                    history?.markMessageDeleted(
                        channelId = mutation.channelId,
                        messageId = mutation.messageId,
                    )
                }
                is TwitchChatMutationEvent.UserMessagesCleared -> {
                    state.markUserMessagesDeleted(
                        channelId = mutation.channelId,
                        userId = mutation.userId,
                    )
                    history?.markUserMessagesDeleted(
                        channelId = mutation.channelId,
                        userId = mutation.userId,
                    )
                }
                is TwitchChatMutationEvent.ChatCleared -> {
                    state.clearChannelMessages(mutation.channelId)
                    history?.clearChannel(mutation.channelId)
                }
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
        val evaluator = currentMessageRuleEvaluator()
        val decoration = evaluator.evaluate(message)

        // Persist the one-time decision before the message becomes visible to Compose. This keeps
        // Ignore -> Highlight precedence stable even if rules are edited on the next frame.
        messageRules?.recordDecoration(message.id, decoration)
        state.append(message)
        attention.recordIncoming(
            message = message,
            session = session,
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
        history?.saveMessage(message)
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
            session = session,
        )

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
            onFatalSessionError(authenticationFailure)
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
