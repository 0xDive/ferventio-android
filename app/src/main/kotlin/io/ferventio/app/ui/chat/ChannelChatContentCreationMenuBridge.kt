package io.ferventio.app.ui

import androidx.compose.runtime.Composable
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConfirmedModerationCommand
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.ThirdPartyEmoteAsset

/**
 * Default chat-content entry point for callers that do not provide custom composer-leading UI.
 * Keeps creation actions beside the composer without coupling workspace/pager code to menu details.
 */
@Composable
internal fun ChannelChatContent(
    state: FerventioUiState,
    channelId: String,
    interactiveChatState: InteractiveChatOverlayState = InteractiveChatOverlayState(),
    onSend: (String, String?) -> Boolean,
    onExecuteNuke: (NukeExecutionPlan) -> Unit = {},
    onExecuteModerationCommand: (ConfirmedModerationCommand) -> Unit = {},
    onCreatePoll: (PollDraft) -> Unit = {},
    onCreatePrediction: (PredictionDraft) -> Unit = {},
    onEndPoll: (String, Boolean) -> Unit = { _, _ -> },
    onLockPrediction: (String) -> Unit = {},
    onCancelPrediction: (String) -> Unit = {},
    onResolvePrediction: (String, String) -> Unit = { _, _ -> },
    onRecoverInteractiveMutation: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onRetryMessage: (ChatMessage) -> Unit = {},
    onDeleteMessage: (ChatMessage) -> Unit = {},
    onQuickBan: (ChatMessage) -> Unit = {},
    onPinMessage: (ChatMessage) -> Unit = {},
    onUnpinMessage: (String, String) -> Unit = { _, _ -> },
    onRefreshPinnedMessage: (String) -> Unit = {},
    onAutoModDecision: (String, Boolean) -> Unit = { _, _ -> },
    onEmoteUsed: (ThirdPartyEmoteAsset) -> Unit = {},
    onToggleFavoriteEmote: (ThirdPartyEmoteAsset) -> Unit = {},
    onOpenUser: (ChatMessage) -> Unit,
    onScrollPositionChanged: (String, String?, Int, Int, Boolean) -> Unit,
    onLoadOlderHistory: () -> Unit = {},
    onUserInteraction: () -> Unit,
    filterQuery: String = "",
    unreadCount: Int = 0,
    navigationTargetMessageId: String? = null,
    onNavigationConsumed: (String) -> Unit = {},
    replyTargetMessageId: String? = null,
    onReplyTargetConsumed: (String) -> Unit = {},
    onMarkRead: () -> Unit = {},
    isReadActive: Boolean = true,
    onHorizontalGestureLockChanged: (Boolean) -> Unit = {},
    repeatCollapseEnabled: Boolean? = null,
    instanceKey: String = channelId,
) {
    ChannelChatContent(
        state = state,
        channelId = channelId,
        interactiveChatState = interactiveChatState,
        onSend = onSend,
        onExecuteNuke = onExecuteNuke,
        onExecuteModerationCommand = onExecuteModerationCommand,
        onCreatePoll = onCreatePoll,
        onCreatePrediction = onCreatePrediction,
        onEndPoll = onEndPoll,
        onLockPrediction = onLockPrediction,
        onCancelPrediction = onCancelPrediction,
        onResolvePrediction = onResolvePrediction,
        onRecoverInteractiveMutation = onRecoverInteractiveMutation,
        onDraftChange = onDraftChange,
        onRetryMessage = onRetryMessage,
        onDeleteMessage = onDeleteMessage,
        onQuickBan = onQuickBan,
        onPinMessage = onPinMessage,
        onUnpinMessage = onUnpinMessage,
        onRefreshPinnedMessage = onRefreshPinnedMessage,
        onAutoModDecision = onAutoModDecision,
        onEmoteUsed = onEmoteUsed,
        onToggleFavoriteEmote = onToggleFavoriteEmote,
        onOpenUser = onOpenUser,
        onScrollPositionChanged = onScrollPositionChanged,
        onLoadOlderHistory = onLoadOlderHistory,
        onUserInteraction = onUserInteraction,
        filterQuery = filterQuery,
        unreadCount = unreadCount,
        navigationTargetMessageId = navigationTargetMessageId,
        onNavigationConsumed = onNavigationConsumed,
        replyTargetMessageId = replyTargetMessageId,
        onReplyTargetConsumed = onReplyTargetConsumed,
        onMarkRead = onMarkRead,
        isReadActive = isReadActive,
        onHorizontalGestureLockChanged = onHorizontalGestureLockChanged,
        repeatCollapseEnabled = repeatCollapseEnabled,
        composerLeadingContent = {
            InteractiveChatCreationMenu(
                state = state,
                channelId = channelId,
                interactiveChatState = interactiveChatState,
                onCreatePoll = onCreatePoll,
                onCreatePrediction = onCreatePrediction,
            )
        },
        instanceKey = instanceKey,
    )
}
