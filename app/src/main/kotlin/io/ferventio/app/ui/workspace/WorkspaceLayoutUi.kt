package io.ferventio.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChannelAttention
import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatSearchDateRange
import io.ferventio.app.domain.ChatSearchParser
import io.ferventio.app.domain.ChatSearchRequest
import io.ferventio.app.domain.ChatSearchScope
import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.FilteredSplit
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.MainSection
import io.ferventio.app.domain.MAX_SPLITS_PER_TAB
import io.ferventio.app.domain.SplitLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.app.push.PushUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceTabStrip(
    state: FerventioUiState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
) {
    val tabs = state.workspaceLayout.activeWorkspace?.tabs.orEmpty()
    if (tabs.isEmpty()) return
    val selectedId = state.workspaceLayout.activeWorkspace?.activeTabId
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.id }) { tab ->
            FilterChip(
                selected = tab.id == selectedId,
                onClick = {
                    hideKeyboard()
                    controller.selectWorkspaceTab(tab.id)
                },
                label = { VerbatimText(tab.title) },
            )
        }
        item {
            IconButton(onClick = {
                hideKeyboard()
                controller.addWorkspaceTab()
            }) {
                Icon(Icons.Default.Add, contentDescription = localizedString("Новая вкладка"))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhoneChannelPager(
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
) {
    val channels = state.channels
    val channelIds = remember(channels) { channels.map(ChatChannel::id) }
    val selectedIndex = channelIds.indexOf(state.selectedChannelId)
        .takeIf { it >= 0 }
        ?: 0
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, channels.lastIndex),
        pageCount = { channels.size },
    )
    var horizontalPagerLocked by remember { mutableStateOf(false) }
    val latestChannelIds by rememberUpdatedState(channelIds)

    // Synchronize only when selection changes externally (drawer, mention, search).
    // Keying this effect by isScrollInProgress caused a race at the end of a swipe:
    // the old selected channel could pull the pager back before settledPage updated the controller.
    LaunchedEffect(state.selectedChannelId, channelIds) {
        val target = channelIds.indexOf(state.selectedChannelId)
        if (target >= 0 && target != pagerState.settledPage) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) hideKeyboard()
    }
    // Keep this collector alive when only the list order changes. Restarting it for a reorder
    // would emit the old page index against the new list and briefly select the wrong channel.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                latestChannelIds.getOrNull(page)?.let { channelId ->
                    hideKeyboard()
                    controller.selectChannel(channelId)
                }
            }
    }
    // A reordered channel list can temporarily leave the pager on an index that now belongs to
    // another channel. Do not mark that transient page visible until selection and pager agree.
    LaunchedEffect(pagerState.settledPage, channelIds, state.selectedChannelId) {
        val settledChannelId = channelIds.getOrNull(pagerState.settledPage)
        controller.setVisibleChannels(
            settledChannelId
                ?.takeIf { it == state.selectedChannelId }
                ?.let(::setOf)
                .orEmpty(),
        )
    }
    DisposableEffect(Unit) {
        onDispose { controller.setVisibleChannels(emptySet()) }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> channels[index].id },
            // Compose only the active page while idle. Adjacent chats are created on demand
            // during the swipe instead of recomposing in the background for every message.
            beyondViewportPageCount = 0,
            userScrollEnabled = !horizontalPagerLocked,
        ) { page ->
            val channel = channels[page]
            val channelAttention = state.channelAttention[channel.id]
            key(channel.id) {
                ChannelChatContent(
                    state = state,
                    channelId = channel.id,
                    interactiveChatState = interactiveChatState,
                    onSend = { text, reply -> controller.sendMessageToChannel(channel.id, text, reply) },
                    onExecuteNuke = { plan -> controller.executeNuke(channel.id, plan) },

                    onExecuteModerationCommand = { command -> controller.executeConfirmedModerationCommand(channel.id, command) },

                    onCreatePoll = { draft -> controller.createInteractivePoll(channel.id, draft) },

                    onCreatePrediction = { draft -> controller.createInteractivePrediction(channel.id, draft) },

                    onEndPoll = { pollId, archive -> controller.endInteractivePoll(channel.id, pollId, archive) },

                    onLockPrediction = { predictionId -> controller.lockInteractivePrediction(channel.id, predictionId) },

                    onCancelPrediction = { predictionId -> controller.cancelInteractivePrediction(channel.id, predictionId) },

                    onResolvePrediction = { predictionId, outcomeId -> controller.resolveInteractivePrediction(channel.id, predictionId, outcomeId) },
                    onRecoverInteractiveMutation = { controller.recoverInteractiveMutation(channel.id) },
                    onDraftChange = { controller.updateDraft(channel.id, it) },
                    onRetryMessage = controller::retryOutgoingMessage,
                    onDeleteMessage = controller::deleteMessage,
                    onQuickBan = controller::banUser,
                    onPinMessage = controller::pinMessage,
                    onUnpinMessage = controller::unpinMessage,
                    onRefreshPinnedMessage = controller::refreshPinnedMessage,
                    onAutoModDecision = controller::decideAutoModMessage,
                    onEmoteUsed = controller::recordEmoteUsage,
                    onToggleFavoriteEmote = controller::toggleFavoriteEmote,
                    onOpenUser = { message ->
                        hideKeyboard()
                        controller.openUserCard(message)
                    },
                    onScrollPositionChanged = controller::saveScrollPosition,
                    onLoadOlderHistory = { controller.loadOlderHistory(channel.id) },
                    onUserInteraction = hideKeyboard,
                    unreadCount = channelAttention?.unreadCount ?: 0,
                    navigationTargetMessageId = state.messageNavigationTargets[channel.id],
                    onNavigationConsumed = { messageId ->
                        controller.consumeMessageNavigation(channel.id, messageId)
                    },
                    replyTargetMessageId = state.replyComposerTargets[channel.id],
                    onReplyTargetConsumed = { messageId ->
                        controller.consumeReplyComposerTarget(channel.id, messageId)
                    },
                    onMarkRead = { controller.markChannelRead(channel.id) },
                    isReadActive = channel.id == channelIds.getOrNull(pagerState.settledPage) &&
                        channel.id in state.visibleChannelIds,
                    onHorizontalGestureLockChanged = { locked ->
                        if (channel.id == state.selectedChannelId) horizontalPagerLocked = locked
                    },
                    instanceKey = "phone-${channel.id}",
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhoneChannelTabs(
    channels: List<ChatChannel>,
    selectedChannelId: String?,
    pinnedIds: Set<String>,
    tabTitles: Map<String, String>,
    attention: Map<String, ChannelAttention>,
    onSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }
    LaunchedEffect(selectedIndex, channels.map(ChatChannel::id)) {
        if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(channels, key = ChatChannel::id) { channel ->
            val selected = channel.id == selectedChannelId
            val channelAttention = attention[channel.id]
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { onSelect(channel.id) },
                    onLongClick = { onLongPress(channel.id) },
                ),
                shape = MaterialTheme.shapes.large,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (channel.id in pinnedIds) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    VerbatimText(
                        text = tabTitles[channel.id]?.takeIf(String::isNotBlank) ?: "#${channel.displayName}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if ((channelAttention?.mentionCount ?: 0) > 0) {
                        Spacer(Modifier.width(5.dp))
                        Badge { VerbatimText(compactCount(channelAttention?.mentionCount ?: 0)) }
                    } else if ((channelAttention?.unreadCount ?: 0) > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            modifier = Modifier.size(7.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primary,
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
internal fun WideSplitLayout(
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    tab: WorkspaceTab,
    hideKeyboard: () -> Unit,
) {
    val splits = tab.splits.take(MAX_SPLITS_PER_TAB)
    val visibleChannels = splits.mapNotNull(SplitLayout::channelId).toSet()
    LaunchedEffect(visibleChannels) { controller.setVisibleChannels(visibleChannels) }
    DisposableEffect(Unit) {
        onDispose { controller.setVisibleChannels(emptySet()) }
    }

    when (splits.size) {
        0 -> Unit
        1 -> SplitPane(
            split = splits.first(),
            splitCount = 1,
            activeSplitId = tab.activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
            modifier = Modifier.fillMaxSize(),
        )
        2 -> ResizableTwoPane(
            splits = splits,
            fraction = tab.primaryFraction,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
        )
        else -> SplitGrid(
            splits = splits,
            fraction = tab.primaryFraction,
            activeSplitId = tab.activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
        )
    }
}

@Composable
internal fun ResizableTwoPane(
    splits: List<SplitLayout>,
    fraction: Float,
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
) {
    var widthPx by remember { mutableStateOf(1) }
    var localFraction by remember { mutableStateOf(fraction) }
    LaunchedEffect(fraction) { localFraction = fraction }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
    ) {
        SplitPane(
            split = splits[0],
            splitCount = 2,
            activeSplitId = state.workspaceLayout.activeTab?.activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
            modifier = Modifier.weight(localFraction),
        )
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(10.dp)
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { controller.setSplitPrimaryFraction(localFraction) },
                        onDragCancel = { localFraction = fraction },
                    ) { _, dragAmount ->
                        localFraction = (localFraction + dragAmount / widthPx.toFloat())
                            .coerceIn(0.25f, 0.75f)
                    }
                },
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
        SplitPane(
            split = splits[1],
            splitCount = 2,
            activeSplitId = state.workspaceLayout.activeTab?.activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
            modifier = Modifier.weight(1f - localFraction),
        )
    }
}

@Composable
internal fun SplitGrid(
    splits: List<SplitLayout>,
    fraction: Float,
    activeSplitId: String?,
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
) {
    val left = splits.filterIndexed { index, _ -> index % 2 == 0 }
    val right = splits.filterIndexed { index, _ -> index % 2 == 1 }
    var widthPx by remember { mutableStateOf(1) }
    var localFraction by remember { mutableStateOf(fraction) }
    LaunchedEffect(fraction) { localFraction = fraction }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
    ) {
        SplitColumn(
            splits = left,
            splitCount = splits.size,
            activeSplitId = activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
            modifier = Modifier.weight(localFraction),
        )
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { controller.setSplitPrimaryFraction(localFraction) },
                        onDragCancel = { localFraction = fraction },
                    ) { _, dragAmount ->
                        localFraction = (localFraction + dragAmount / widthPx.toFloat())
                            .coerceIn(0.25f, 0.75f)
                    }
                },
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
        SplitColumn(
            splits = right,
            splitCount = splits.size,
            activeSplitId = activeSplitId,
            state = state,
            interactiveChatState = interactiveChatState,
            controller = controller,
            hideKeyboard = hideKeyboard,
            modifier = Modifier.weight(1f - localFraction),
        )
    }
}

@Composable
internal fun SplitColumn(
    splits: List<SplitLayout>,
    splitCount: Int,
    activeSplitId: String?,
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxHeight()) {
        splits.forEachIndexed { index, split ->
            SplitPane(
                split = split,
                splitCount = splitCount,
                activeSplitId = activeSplitId,
                state = state,
                interactiveChatState = interactiveChatState,
                controller = controller,
                hideKeyboard = hideKeyboard,
                modifier = Modifier.weight(1f),
            )
            if (index < splits.lastIndex) HorizontalDivider(thickness = 6.dp)
        }
    }
}

@Composable
internal fun SplitPane(
    split: SplitLayout,
    splitCount: Int,
    activeSplitId: String?,
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    hideKeyboard: () -> Unit,
    modifier: Modifier,
) {
    var showChannels by rememberSaveable(split.id) { mutableStateOf(false) }
    var showFilterEditor by rememberSaveable(split.id) { mutableStateOf(false) }
    val channel = split.channelId?.let { id -> state.channels.firstOrNull { it.id == id } }
    val attention = split.channelId?.let(state.channelAttention::get)

    Surface(
        modifier = modifier.padding(2.dp),
        tonalElevation = if (split.id == activeSplitId) 2.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        hideKeyboard()
                        controller.focusChatSplit(split.id)
                        channel?.id?.let(controller::selectChannel)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = {
                        hideKeyboard()
                        showChannels = true
                    }) {
                        if (channel == null) {
                            LocalizedText(
                                "Выбрать канал",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            VerbatimText(
                                "#${channel.displayName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showChannels,
                        onDismissRequest = { showChannels = false },
                    ) {
                        state.channels.forEach { candidate ->
                            DropdownMenuItem(
                                text = { VerbatimText("#${candidate.displayName}") },
                                onClick = {
                                    hideKeyboard()
                                    controller.setChatSplitChannel(split.id, candidate.id)
                                    showChannels = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if ((attention?.mentionCount ?: 0) > 0) {
                    Badge { VerbatimText(compactCount(attention?.mentionCount ?: 0)) }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = {
                    hideKeyboard()
                    showFilterEditor = true
                }) {
                    Icon(Icons.Default.FilterAlt, contentDescription = localizedString("Фильтр split"))
                }
                if (splitCount > 1) {
                    IconButton(onClick = {
                        hideKeyboard()
                        controller.removeChatSplit(split.id)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = localizedString("Закрыть split"))
                    }
                }
            }
            if (channel == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LocalizedText("Выбери канал для split")
                }
            } else {
                key(split.id, channel.id) {
                    ChannelChatContent(
                        state = state,
                        channelId = channel.id,
                        interactiveChatState = interactiveChatState,
                        onSend = { text, reply -> controller.sendMessageToChannel(channel.id, text, reply) },
                        onExecuteNuke = { plan -> controller.executeNuke(channel.id, plan) },

                        onExecuteModerationCommand = { command -> controller.executeConfirmedModerationCommand(channel.id, command) },

                        onCreatePoll = { draft -> controller.createInteractivePoll(channel.id, draft) },

                        onCreatePrediction = { draft -> controller.createInteractivePrediction(channel.id, draft) },

                        onEndPoll = { pollId, archive -> controller.endInteractivePoll(channel.id, pollId, archive) },

                        onLockPrediction = { predictionId -> controller.lockInteractivePrediction(channel.id, predictionId) },

                        onCancelPrediction = { predictionId -> controller.cancelInteractivePrediction(channel.id, predictionId) },

                        onResolvePrediction = { predictionId, outcomeId -> controller.resolveInteractivePrediction(channel.id, predictionId, outcomeId) },
                        onRecoverInteractiveMutation = { controller.recoverInteractiveMutation(channel.id) },
                        onDraftChange = { controller.updateDraft(channel.id, it) },
                        onRetryMessage = controller::retryOutgoingMessage,
                        onDeleteMessage = controller::deleteMessage,
                        onQuickBan = controller::banUser,
                        onPinMessage = controller::pinMessage,
                        onUnpinMessage = controller::unpinMessage,
                        onRefreshPinnedMessage = controller::refreshPinnedMessage,
                        onAutoModDecision = controller::decideAutoModMessage,
                        onEmoteUsed = controller::recordEmoteUsage,
                        onToggleFavoriteEmote = controller::toggleFavoriteEmote,
                        onOpenUser = { message ->
                            hideKeyboard()
                            controller.openUserCard(message)
                        },
                        onScrollPositionChanged = { channelId, anchor, index, offset, atBottom ->
                            if (split.id == activeSplitId && split.filterQuery.isBlank()) {
                                controller.saveScrollPosition(channelId, anchor, index, offset, atBottom)
                            }
                        },
                        onLoadOlderHistory = { controller.loadOlderHistory(channel.id) },
                        onUserInteraction = {
                            hideKeyboard()
                            controller.focusChatSplit(split.id)
                        },
                        filterQuery = split.filterQuery,
                        unreadCount = attention?.unreadCount ?: 0,
                        navigationTargetMessageId = state.messageNavigationTargets[channel.id],
                        onNavigationConsumed = { messageId ->
                            controller.consumeMessageNavigation(channel.id, messageId)
                        },
                        replyTargetMessageId = state.replyComposerTargets[channel.id],
                        onReplyTargetConsumed = { messageId ->
                            controller.consumeReplyComposerTarget(channel.id, messageId)
                        },
                        onMarkRead = { controller.markChannelRead(channel.id) },
                        instanceKey = split.id,
                    )
                }
            }
        }
    }

    if (showFilterEditor) {
        SplitFilterEditorDialog(
            initialExpression = split.filterQuery,
            savedFilters = state.savedMessageFilters,
            messages = split.channelId?.let(state.messagesByChannel::get).orEmpty(),
            isHighlightsSplit = { message ->
                state.messageDecorationsById[message.id]?.filteredSplit == true
            },
            onDismiss = { showFilterEditor = false },
            onSave = { expression ->
                controller.setChatSplitFilter(split.id, expression)
                showFilterEditor = false
            },
        )
    }
}
