package io.ferventio.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ferventio.app.BuildConfig
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatAssetResolver
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ComposerAutocomplete
import io.ferventio.app.domain.ComposerEmoteVisuals
import io.ferventio.app.domain.ComposerSuggestion
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageTextPreparation
import io.ferventio.app.domain.ChatLinkParser
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.resolveSplitFilterExpression
import io.ferventio.app.domain.ChatRateLimitState
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.CheermoteResolver
import io.ferventio.app.domain.EmoteCatalogRanking
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ChatNoticeTextFormatter
import io.ferventio.app.domain.ChatPresentationPolicy
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.SettingsSyncStatus
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.ReplyThreadResolver
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.ScrollRestorationPolicy
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.domain.resolve
import io.ferventio.app.domain.usageKey
import io.ferventio.app.domain.ThirdPartyEmoteCatalogResolver
import io.ferventio.app.domain.UserCardUiState
import io.ferventio.app.push.PushCoordinator
import io.ferventio.app.push.PushStatus
import io.ferventio.app.push.PushUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.core.graphics.toColorInt


@Composable
internal fun ChannelChatContent(
    state: FerventioUiState,
    channelId: String,
    onSend: (String, String?) -> Boolean,
    onDraftChange: (String) -> Unit = {},
    onRetryMessage: (ChatMessage) -> Unit = {},
    onDeleteMessage: (ChatMessage) -> Unit = {},
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
    instanceKey: String = channelId,
) {
    val input = state.draftsByChannel[channelId].orEmpty()
    val canWrite = state.isAuthenticated
    var replyTarget by remember(instanceKey) { mutableStateOf<ChatMessage?>(null) }
    var replyThreadTarget by remember(instanceKey) { mutableStateOf<ChatMessage?>(null) }
    var messageActionsTarget by remember(instanceKey) { mutableStateOf<ChatMessage?>(null) }
    var highlightedMessageId by remember(instanceKey) { mutableStateOf<String?>(null) }
    var autocompleteIndex by remember(instanceKey) { mutableIntStateOf(0) }
    var historyIndex by remember(instanceKey) { mutableIntStateOf(-1) }
    var historyScratch by remember(instanceKey) { mutableStateOf("") }
    var showEmotePicker by rememberSaveable(instanceKey) { mutableStateOf(false) }
    var emoteDetails by remember(instanceKey) { mutableStateOf<EmoteDisplayInfo?>(null) }
    BackHandler(enabled = showEmotePicker) { showEmotePicker = false }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    @Suppress("DEPRECATION") // LocalClipboard migration requires suspend clipboard writes.
    val clipboardManager = LocalClipboardManager.current
    val latestOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val hideKeyboard: () -> Unit = remember(keyboardController, focusManager) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            latestOnUserInteraction()
        }
    }
    val rawMessages = state.messagesByChannel[channelId].orEmpty()
    val historyPaging = state.historyPagingByChannel[channelId]
    val filterExpression = remember(filterQuery, state.savedMessageFilters) {
        resolveSplitFilterExpression(filterQuery, state.savedMessageFilters).trim()
    }
    val compiledSplitFilter = remember(filterExpression) {
        if (filterExpression.isBlank() || filterExpression == HIGHLIGHTS_FILTER_QUERY) null
        else MessageFilterLanguage.compileForSplit(filterExpression)
    }
    val hasActiveIgnoreRules = remember(state.ignoreRules) { state.ignoreRules.any(IgnoreRule::enabled) }
    val needsDecorationFiltering = filterExpression == HIGHLIGHTS_FILTER_QUERY || hasActiveIgnoreRules
    val messageDecorations = state.messageDecorationsById
    val filteringDecorations = if (needsDecorationFiltering) messageDecorations else EMPTY_MESSAGE_DECORATIONS
    val messages = remember(
        rawMessages,
        state.showSystemMessages,
        filterExpression,
        compiledSplitFilter,
        needsDecorationFiltering,
        filteringDecorations,
    ) {
        val needsSystemFiltering = !state.showSystemMessages
        if (filterExpression.isEmpty() && !needsDecorationFiltering && !needsSystemFiltering) {
            // Normal chat is the hottest path. Return the existing immutable list rather than
            // allocating and scanning a second list after every incoming message.
            rawMessages
        } else {
            rawMessages.filter { message ->
                if (needsSystemFiltering && message.type in SYSTEM_MESSAGE_TYPES) return@filter false
                val decoration = filteringDecorations[message.id]
                val matchesFilter = when {
                    filterExpression.isEmpty() -> true
                    filterExpression == HIGHLIGHTS_FILTER_QUERY -> decoration?.filteredSplit == true
                    else -> compiledSplitFilter?.matches(message) == true
                }
                matchesFilter && decoration?.ignoreDisplayMode != IgnoreDisplayMode.HIDE
            }
        }
    }
    val heldAutoModMessages = remember(state.moderation.autoModQueue, channelId) {
        state.moderation.autoModQueue
            .asSequence()
            .filter { it.channelId == channelId && it.status == AutoModMessageStatus.HELD }
            .sortedBy(AutoModHeldMessage::heldAtMillis)
            .toList()
    }
    val totalContentCount = messages.size + heldAutoModMessages.size
    val hasChatContent = totalContentCount > 0
    val lastContentIndex = totalContentCount - 1
    val savedPosition = state.scrollPositionsByChannel[channelId]
    val restoreOldPosition = savedPosition?.isAtBottom == false
    val initialIndex = remember(
        instanceKey,
        hasChatContent,
        savedPosition?.anchorMessageId,
        savedPosition?.firstVisibleItemIndex,
        savedPosition?.firstVisibleItemScrollOffset,
        savedPosition?.isAtBottom,
    ) {
        when {
            !hasChatContent -> 0
            restoreOldPosition && messages.isNotEmpty() -> ScrollRestorationPolicy.targetIndex(messages, savedPosition)
                ?: savedPosition.firstVisibleItemIndex.coerceIn(0, messages.lastIndex)
            else -> lastContentIndex
        }
    }
    val initialOffset = remember(
        instanceKey,
        savedPosition?.firstVisibleItemScrollOffset,
        restoreOldPosition,
    ) {
        if (restoreOldPosition) savedPosition.firstVisibleItemScrollOffset.coerceAtLeast(0) else 0
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    val coroutineScope = rememberCoroutineScope()
    val visibleMessageIds by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .asSequence()
                .mapNotNull { item -> item.key as? String }
                .filterNot { key -> key.startsWith("automod:") || key == HISTORY_LOADING_KEY }
                .toSet()
        }
    }
    val isViewportAtBottom by remember(listState, lastContentIndex) {
        derivedStateOf {
            lastContentIndex < 0 ||
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= lastContentIndex
        }
    }
    val latestMessageId = messages.lastOrNull()?.id
    val latestAutoModMessageId = heldAutoModMessages.lastOrNull()?.messageId
    val liveContentKey = "${latestMessageId.orEmpty()}|${latestAutoModMessageId.orEmpty()}"
    var lastObservedLiveContentKey by remember(instanceKey) { mutableStateOf(liveContentKey) }
    val latestMessages by rememberUpdatedState(messages)
    val latestLastContentIndex by rememberUpdatedState(lastContentIndex)
    var initialPositionApplied by remember(instanceKey) {
        // When messages are already in memory, rememberLazyListState has received the correct
        // initial anchor and the page can be shown immediately. Empty precomposed neighbours keep
        // the anchor pending until their first non-empty snapshot arrives.
        mutableStateOf(hasChatContent)
    }
    var followLiveChat by remember(instanceKey) { mutableStateOf(state.autoScrollEnabled && !restoreOldPosition) }
    val latestFollowLiveChat by rememberUpdatedState(followLiveChat)
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val catalog = state.emoteCatalogByChannel[channelId].orEmpty()
    val emoteCatalogByProviderAndId = remember(catalog) {
        catalog.associateBy { asset -> asset.provider to asset.id }
    }
    val composerEmoteIndex = remember(catalog) { ComposerEmoteVisuals.buildIndex(catalog) }
    val composerRichText = remember(input, composerEmoteIndex, state.showComposerEmoteImages) {
        if (state.showComposerEmoteImages) buildComposerRichText(input, composerEmoteIndex) else null
    }
    val composerVisualTransformation = remember(composerRichText) {
        composerRichText?.let(::ComposerVisualTransformation) ?: VisualTransformation.None
    }
    val profilesById = state.userProfilesById
    val currentUserId = state.session?.userId
    val needsUserSuggestions = remember(input) {
        ComposerAutocomplete.currentToken(input).startsWith("@")
    }
    val userSuggestionIndex = if (needsUserSuggestions) {
        remember(rawMessages, profilesById, currentUserId) {
            ComposerAutocomplete.buildUserIndex(
                messages = rawMessages,
                profilesById = profilesById,
                currentUserId = currentUserId,
            )
        }
    } else {
        emptyList()
    }
    val suggestions = remember(
        input,
        userSuggestionIndex,
        catalog,
        state.recentEmoteKeys,
        state.favoriteEmoteKeys,
    ) {
        ComposerAutocomplete.suggestions(
            input = input,
            messages = emptyList(),
            profilesById = emptyMap(),
            catalog = catalog,
            recentEmoteKeys = state.recentEmoteKeys,
            favoriteEmoteKeys = state.favoriteEmoteKeys,
            currentUserId = currentUserId,
            userIndex = userSuggestionIndex,
        )
    }
    LaunchedEffect(showEmotePicker, suggestions.isNotEmpty()) {
        onHorizontalGestureLockChanged(showEmotePicker || suggestions.isNotEmpty())
    }
    DisposableEffect(instanceKey) {
        onDispose { onHorizontalGestureLockChanged(false) }
    }

    val twitchBadgeAssets = state.badgeAssetsByChannel[channelId].orEmpty()
    val globalFfzBadgesByUser = state.frankerFaceZBadgesByUserId
    val channelFfzBadgesByUser = state.frankerFaceZChannelBadgesByChannel[channelId].orEmpty()
    val ffzBadgesByUser = remember(globalFfzBadgesByUser, channelFfzBadgesByUser) {
        (globalFfzBadgesByUser.keys + channelFfzBadgesByUser.keys).associateWith { userId ->
            (globalFfzBadgesByUser[userId].orEmpty() + channelFfzBadgesByUser[userId].orEmpty())
                .distinctBy(ChatBadgeAsset::key)
        }
    }
    val cheermoteAssets = state.cheermoteAssetsByChannel[channelId].orEmpty()
    val messageRenderAssets = remember(twitchBadgeAssets, cheermoteAssets, emoteCatalogByProviderAndId) {
        MessageRenderAssets(
            badgeAssets = twitchBadgeAssets,
            cheermoteAssets = cheermoteAssets,
            emoteCatalogByProviderAndId = emoteCatalogByProviderAndId,
        )
    }
    val ffzBadgeListsByUser = remember(ffzBadgesByUser) {
        ffzBadgesByUser.mapValues { (_, badges) -> ImmutableBadgeAssetList(badges) }
    }
    val sentHistory = state.sentMessageHistoryByChannel[channelId].orEmpty()
    val pinnedMessage = state.pinnedMessagesByChannel[channelId]?.takeIf { pinned ->
        pinned.endsAt?.let { endsAt ->
            runCatching { Instant.parse(endsAt).isAfter(Instant.now()) }.getOrDefault(false)
        } ?: true
    }
    val canManagePinnedMessages = state.isAuthenticated && channelId in state.moderatedChannelIds
    LaunchedEffect(channelId) {
        onRefreshPinnedMessage(channelId)
        while (true) {
            delay(PINNED_MESSAGE_REFRESH_INTERVAL_MILLIS)
            onRefreshPinnedMessage(channelId)
        }
    }

    LaunchedEffect(suggestions.map(ComposerSuggestion::key)) {
        autocompleteIndex = autocompleteIndex.coerceIn(0, suggestions.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(1_800)
            highlightedMessageId = null
        }
    }

    val latestMessagesForNavigation = rememberUpdatedState(messages)
    val navigateToMessage: (String) -> Unit = remember(instanceKey, listState, coroutineScope) {
        { targetId ->
            val currentMessages = latestMessagesForNavigation.value
            val targetIndex = currentMessages.indexOfFirst {
                it.id == targetId || it.serverMessageId == targetId
            }
            if (targetIndex >= 0) {
                followLiveChat = false
                highlightedMessageId = currentMessages[targetIndex].id
                coroutineScope.launch { listState.scrollToItem(targetIndex) }
            }
        }
    }

    val applySuggestion: (ComposerSuggestion) -> Unit = { suggestion ->
        onDraftChange(ComposerAutocomplete.applySuggestion(input, suggestion))
        autocompleteIndex = 0
        historyIndex = -1
    }

    val submitMessage: () -> Unit = submit@{
        val message = input
        if (message.isBlank()) return@submit
        if (onSend(message, replyTarget?.id)) {
            EmoteCatalogRanking.usedInText(message, catalog).forEach(onEmoteUsed)
            onDraftChange("")
            replyTarget = null
            historyIndex = -1
            historyScratch = ""
            autocompleteIndex = 0
        }
    }

    val moveThroughHistory: (Boolean) -> Unit = { older ->
        if (sentHistory.isNotEmpty()) {
            if (historyIndex < 0) historyScratch = input
            historyIndex = if (older) {
                (historyIndex + 1).coerceAtMost(sentHistory.lastIndex)
            } else {
                (historyIndex - 1).coerceAtLeast(-1)
            }
            onDraftChange(if (historyIndex >= 0) sentHistory[historyIndex] else historyScratch)
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            // Pause before hiding IME. Otherwise the viewport expansion can race the drag and
            // the live-follow observer would pull the user back to the newest message.
            followLiveChat = false
            hideKeyboard()
        }
    }

    LaunchedEffect(
        channelId,
        listState,
        historyPaging?.isLoading,
        historyPaging?.endReached,
        rawMessages.firstOrNull()?.id,
    ) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { firstVisibleIndex ->
                if (
                    firstVisibleIndex <= HISTORY_PREFETCH_THRESHOLD &&
                    historyPaging?.isLoading != true &&
                    historyPaging?.endReached != true &&
                    rawMessages.isNotEmpty()
                ) {
                    onLoadOlderHistory()
                }
            }
    }

    LaunchedEffect(
        channelId,
        initialPositionApplied,
        hasChatContent,
        state.isHistoryLoading,
        savedPosition,
    ) {
        if (state.isHistoryLoading || initialPositionApplied || !hasChatContent) return@LaunchedEffect
        val restore = savedPosition?.isAtBottom == false && messages.isNotEmpty()
        val targetIndex = if (restore) {
            ScrollRestorationPolicy.targetIndex(messages, savedPosition)
                ?: savedPosition.firstVisibleItemIndex.coerceIn(0, messages.lastIndex)
        } else {
            lastContentIndex
        }
        val targetOffset = if (restore) savedPosition.firstVisibleItemScrollOffset.coerceAtLeast(0) else 0
        listState.scrollToItem(targetIndex, targetOffset)
        followLiveChat = state.autoScrollEnabled && !restore
        // Wait until LazyColumn has measured the requested anchor before revealing it. This
        // removes the old-message flash when switching between active channels.
        withFrameNanos { }
        initialPositionApplied = true
    }

    LaunchedEffect(channelId, listState, initialPositionApplied) {
        if (!initialPositionApplied) return@LaunchedEffect
        // Observe only scroll start/stop. The previous snapshot included the pixel offset and
        // allocated/processed a new object on nearly every frame of a fling.
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) {
                    // Pause live following for the whole drag/fling. Otherwise a viewport update
                    // can pull the list back to the bottom while the user is still scrolling.
                    followLiveChat = false
                    return@collectLatest
                }
                delay(SCROLL_POSITION_SAVE_DELAY_MILLIS)
                val index = listState.firstVisibleItemIndex
                val atBottom = isViewportAtBottom
                followLiveChat = state.autoScrollEnabled && atBottom
                onScrollPositionChanged(
                    channelId,
                    latestMessages.getOrNull(index)?.id,
                    index,
                    listState.firstVisibleItemScrollOffset,
                    atBottom,
                )
            }
    }

    // IME/system-bar changes alter only the viewport. Do not react to every LazyColumn layout
    // change while the user is dragging or flinging.
    LaunchedEffect(channelId, listState, initialPositionApplied) {
        if (!initialPositionApplied) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportEndOffset }
            .distinctUntilChanged()
            .collectLatest {
                if (
                    state.autoScrollEnabled &&
                    latestFollowLiveChat &&
                    !listState.isScrollInProgress &&
                    latestLastContentIndex >= 0
                ) {
                    listState.scrollToItem(latestLastContentIndex)
                }
            }
    }

    DisposableEffect(channelId, listState) {
        onDispose {
            val index = listState.firstVisibleItemIndex
            val atBottom = latestFollowLiveChat || isViewportAtBottom
            onScrollPositionChanged(
                channelId,
                latestMessages.getOrNull(index)?.id,
                index,
                listState.firstVisibleItemScrollOffset,
                atBottom,
            )
        }
    }

    navigationTargetMessageId?.takeIf(String::isNotBlank)?.let { targetId ->
        LaunchedEffect(targetId, initialPositionApplied, messages) {
            if (!initialPositionApplied) return@LaunchedEffect
            val targetIndex = messages.indexOfFirst { it.id == targetId || it.serverMessageId == targetId }
            if (targetIndex >= 0) {
                followLiveChat = false
                highlightedMessageId = messages[targetIndex].id
                listState.scrollToItem(targetIndex)
            }
            onNavigationConsumed(targetId)
        }
    }

    replyTargetMessageId?.takeIf(String::isNotBlank)?.let { targetId ->
        LaunchedEffect(targetId, messages) {
            val target = messages.firstOrNull { it.id == targetId || it.serverMessageId == targetId }
            if (target != null) replyTarget = target
            onReplyTargetConsumed(targetId)
        }
    }

    LaunchedEffect(
        liveContentKey,
        initialPositionApplied,
        followLiveChat,
        state.autoScrollEnabled,
    ) {
        val hasNewContent = liveContentKey != lastObservedLiveContentKey
        lastObservedLiveContentKey = liveContentKey
        if (!initialPositionApplied || !hasChatContent) return@LaunchedEffect
        if (!state.autoScrollEnabled) {
            if (hasNewContent) followLiveChat = false
            return@LaunchedEffect
        }
        if (!followLiveChat) return@LaunchedEffect
        // Coalesce bursts of incoming messages into one layout jump instead of one scroll job per event.
        delay(LIVE_FOLLOW_COALESCE_MILLIS)
        if (latestFollowLiveChat && latestLastContentIndex >= 0) {
            listState.scrollToItem(latestLastContentIndex)
        }
    }

    LaunchedEffect(initialPositionApplied, followLiveChat, unreadCount, isReadActive) {
        if (isReadActive && initialPositionApplied && followLiveChat && unreadCount > 0) onMarkRead()
    }

    val latestOnOpenUser = rememberUpdatedState(onOpenUser)
    val latestOnRetryMessage = rememberUpdatedState(onRetryMessage)
    val openUserFromRow: (ChatMessage) -> Unit = remember(hideKeyboard) {
        { message ->
            hideKeyboard()
            latestOnOpenUser.value(message)
        }
    }
    val openEmoteFromRow: (EmoteDisplayInfo) -> Unit = remember(hideKeyboard) {
        { info ->
            hideKeyboard()
            emoteDetails = info
        }
    }
    val openReplyThreadFromRow: (ChatMessage) -> Unit = remember(hideKeyboard) {
        { message ->
            hideKeyboard()
            replyThreadTarget = message
        }
    }
    val retryMessageFromRow: (ChatMessage) -> Unit = remember {
        { message -> latestOnRetryMessage.value(message) }
    }
    val openActionsFromRow: (ChatMessage) -> Unit = remember(hideKeyboard) {
        { message ->
            hideKeyboard()
            messageActionsTarget = message
        }
    }
    val replyFromRow: (ChatMessage) -> Unit = remember {
        { message -> replyTarget = message }
    }

    Column(Modifier.fillMaxSize()) {
        if (state.connectionStatus in setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.WAITING_WELCOME,
                ConnectionStatus.CREATING_SUBSCRIPTIONS,
                ConnectionStatus.RECONNECTING,
            )
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        pinnedMessage?.let { pinned ->
            PinnedMessageBanner(
                pinned = pinned,
                canUnpin = canManagePinnedMessages,
                onOpen = { navigateToMessage(pinned.messageId) },
                onUnpin = { onUnpinMessage(channelId, pinned.messageId) },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CHAT_LIST_TEST_TAG)
                    .semantics {
                        if (BuildConfig.PERFORMANCE_TESTING) {
                            contentDescription = CHAT_LIST_TEST_TAG
                        }
                    }
                    .alpha(if (initialPositionApplied || !hasChatContent) 1f else 0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = messages,
                    key = { message -> message.id.ifBlank { message.hashCode().toString() } },
                    contentType = { message -> message.type.name },
                ) { message ->
                    MessageRow(
                        message = message,
                        profile = profilesById[message.userId],
                        hydratedUserColor = state.userColorsById[message.userId],
                        showAvatar = state.showAvatars,
                        showBadges = state.showBadges,
                        showTimestamp = state.showTimestamps,
                        showDeletedMessageContent = state.showDeletedMessageContent,
                        animateEmotes = state.animateEmotes &&
                            !listState.isScrollInProgress &&
                            message.id in visibleMessageIds,
                        emoteScalePercent = state.emoteScalePercent,
                        messageDensity = state.messageDensity,
                        chatNameStyle = state.chatNameStyle,
                        wrapMessageLines = state.wrapMessageLines,
                        mentionColorArgb = state.mentionColorArgb,
                        renderAssets = messageRenderAssets,
                        frankerFaceZBadges = ffzBadgeListsByUser[message.userId]
                            ?: ImmutableBadgeAssetList.Empty,
                        onOpenUser = openUserFromRow,
                        onOpenEmote = openEmoteFromRow,
                        ownUserId = state.session?.userId,
                        highlighted = highlightedMessageId == message.id,
                        decoration = messageDecorations[message.id] ?: MessageDecoration(),
                        onNavigateToMessage = navigateToMessage,
                        onOpenReplyThread = openReplyThreadFromRow,
                        onRetry = retryMessageFromRow,
                        onOpenActions = openActionsFromRow,
                        onReply = replyFromRow,
                    )
                }
                items(
                    items = heldAutoModMessages,
                    key = { held -> "automod:${held.messageId}" },
                    contentType = { "automod-held" },
                ) { held ->
                    AutoModInlineReviewCard(
                        message = held,
                        onApprove = { onAutoModDecision(held.messageId, true) },
                        onDeny = { onAutoModDecision(held.messageId, false) },
                    )
                }
            }

            if (historyPaging?.isLoading == true) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }

            if (initialPositionApplied && hasChatContent && !followLiveChat) {
                FilledTonalButton(
                    onClick = {
                        followLiveChat = state.autoScrollEnabled
                        coroutineScope.launch {
                            listState.scrollToItem(lastContentIndex)
                            onScrollPositionChanged(
                                channelId,
                                messages.lastOrNull()?.id,
                                lastContentIndex,
                                0,
                                true,
                            )
                            if (isReadActive) onMarkRead()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("К актуальному чату")
                }
            }
        }

        replyTarget?.let { target ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Ответ для ${target.userDisplayName}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            target.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                        )
                    }
                    IconButton(onClick = { replyTarget = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Отменить ответ")
                    }
                }
            }
        }

        state.rateLimitsByChannel[channelId]?.let { rateLimit ->
            ChatRateLimitBanner(rateLimit)
        }

        InlineComposerAutocomplete(
            suggestions = suggestions,
            selectedIndex = autocompleteIndex,
            onSelect = applySuggestion,
            onOpenEmoteInfo = { suggestion ->
                hideKeyboard()
                emoteDetails = suggestion.asset.toDisplayInfo()
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = input,
                        enabled = canWrite,
                        onValueChange = { value ->
                            onDraftChange(value.take(500))
                            historyIndex = -1
                            autocompleteIndex = 0
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) showEmotePicker = false
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (suggestions.isNotEmpty()) {
                                            autocompleteIndex = (autocompleteIndex + 1).coerceAtMost(suggestions.lastIndex)
                                        } else {
                                            moveThroughHistory(false)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (suggestions.isNotEmpty()) {
                                            autocompleteIndex = (autocompleteIndex - 1).coerceAtLeast(0)
                                        } else {
                                            moveThroughHistory(true)
                                        }
                                        true
                                    }
                                    Key.Tab -> suggestions.getOrNull(autocompleteIndex)?.let(applySuggestion) != null
                                    Key.Enter -> when {
                                        suggestions.isNotEmpty() && !event.isShiftPressed -> {
                                            suggestions.getOrNull(autocompleteIndex)?.let(applySuggestion)
                                            true
                                        }
                                        state.sendOnEnter && !event.isShiftPressed -> {
                                            submitMessage()
                                            true
                                        }
                                        else -> false
                                    }
                                    else -> false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (composerRichText == null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                        ),
                        visualTransformation = composerVisualTransformation,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (state.sendOnEnter) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (input.isEmpty()) {
                                    Text(
                                        if (canWrite) "Сообщение" else "Войди через Twitch, чтобы писать",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    )
                                } else {
                                    composerRichText?.let { richText ->
                                        Text(
                                            text = richText.annotatedText,
                                            inlineContent = richText.inlineContent,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            maxLines = 4,
                                        )
                                    }
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(
                        onClick = {
                            val opening = !showEmotePicker
                            if (opening) hideKeyboard()
                            showEmotePicker = opening
                        },
                        enabled = canWrite,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.InsertEmoticon,
                            contentDescription = "Каталог эмоутов",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = submitMessage,
                enabled = canWrite && input.isNotBlank(),
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", modifier = Modifier.size(20.dp))
            }
        }

        if (showEmotePicker) {
            TwitchStyleEmotePickerPanel(
                channelId = channelId,
                channelName = state.channels.firstOrNull { it.id == channelId }?.displayName ?: "Канал",
                catalog = catalog,
                recentEmoteKeys = state.recentEmoteKeys,
                favoriteEmoteKeys = state.favoriteEmoteKeys,
                onDismiss = { showEmotePicker = false },
                onSelect = { asset ->
                    onDraftChange(insertEmoteAtEnd(input, asset.code))
                },
                onOpenInfo = { emoteDetails = it.toDisplayInfo() },
                onToggleFavorite = onToggleFavoriteEmote,
            )
        }
    }

    replyThreadTarget?.let { target ->
        ReplyThreadSheet(
            target = target,
            messages = rawMessages,
            onDismiss = { replyThreadTarget = null },
            onNavigate = { messageId ->
                replyThreadTarget = null
                navigateToMessage(messageId)
            },
            onReply = { message ->
                replyThreadTarget = null
                replyTarget = message
            },
        )
    }
    messageActionsTarget?.let { target ->
        MessageActionsSheet(
            message = target,
            canModerate = target.channelId in state.moderatedChannelIds && !target.isDeleted,
            canPin = target.channelId in state.moderatedChannelIds &&
                !target.isSystem &&
                !target.isDeleted &&
                target.outgoingState != OutgoingMessageState.SENDING &&
                target.outgoingState != OutgoingMessageState.FAILED,
            isPinned = pinnedMessage?.messageId == (target.serverMessageId ?: target.id),
            onDismiss = { messageActionsTarget = null },
            onReply = {
                messageActionsTarget = null
                replyTarget = target
            },
            onOpenThread = {
                messageActionsTarget = null
                replyThreadTarget = target
            },
            onNavigateToParent = target.reply?.parentMessageId?.let { parentId ->
                {
                    messageActionsTarget = null
                    navigateToMessage(parentId)
                }
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(target.text))
                messageActionsTarget = null
            },
            onOpenUser = {
                messageActionsTarget = null
                onOpenUser(target)
            },
            onPin = {
                messageActionsTarget = null
                onPinMessage(target)
            },
            onDelete = {
                messageActionsTarget = null
                onDeleteMessage(target)
            },
        )
    }
    emoteDetails?.let { info ->
        EmoteDetailsDialog(info = info, onDismiss = { emoteDetails = null })
    }
}
