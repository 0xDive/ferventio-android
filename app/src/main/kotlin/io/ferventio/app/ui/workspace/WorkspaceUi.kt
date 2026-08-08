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
import io.ferventio.app.R
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
internal fun AuthenticatedShell(
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    pushState: PushUiState,
    onTestPush: () -> Unit,
    onReconnectPush: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onExportCrashReports: () -> Unit,
    onClearCrashReports: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(MainSection.CHATS) }
    var requestedSearchChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val navigate: (MainSection) -> Unit = { destination ->
        hideKeyboard()
        section = if (destination == MainSection.MODERATION) MainSection.CHATS else destination
    }

    BackHandler(enabled = section != MainSection.CHATS) {
        navigate(MainSection.CHATS)
    }

    LaunchedEffect(state.isAuthenticated) {
        if (!state.isAuthenticated && section == MainSection.MODERATION) section = MainSection.CHATS
    }
    LaunchedEffect(state.messageNavigationTargets) {
        if (state.messageNavigationTargets.isNotEmpty()) {
            hideKeyboard()
            section = MainSection.CHATS
        }
    }
    LaunchedEffect(state.requestedMainSection) {
        state.requestedMainSection?.let { requested ->
            navigate(requested)
            controller.consumeRequestedMainSection()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (section) {
            MainSection.CHATS,
            MainSection.MODERATION -> ChatsWorkspaceScreen(
                state = state,
                interactiveChatState = interactiveChatState,
                controller = controller,
                snackbarHostState = snackbarHostState,
                onOpenMentions = { navigate(MainSection.MENTIONS) },
                onOpenSearch = { channelId ->
                    requestedSearchChannelId = channelId
                    navigate(MainSection.SEARCH)
                },
                onOpenSettings = { navigate(MainSection.SETTINGS) },
            )

            MainSection.MENTIONS -> MentionsScreen(
                state = state,
                controller = controller,
                snackbarHostState = snackbarHostState,
                onBack = { navigate(MainSection.CHATS) },
                onOpenEntry = { entry ->
                    controller.openAttentionEntry(entry)
                    navigate(MainSection.CHATS)
                },
            )

            MainSection.SEARCH -> SearchScreen(
                state = state,
                controller = controller,
                initialChannelId = requestedSearchChannelId,
                snackbarHostState = snackbarHostState,
                onBack = { navigate(MainSection.CHATS) },
                onOpenMessage = controller::openSearchResult,
            )

            MainSection.SETTINGS -> SettingsScreen(
                state = state,
                controller = controller,
                snackbarHostState = snackbarHostState,
                pushState = pushState,
                onTestPush = onTestPush,
                onReconnectPush = onReconnectPush,
                onExportSettings = onExportSettings,
                onImportSettings = onImportSettings,
                onExportCrashReports = onExportCrashReports,
                onClearCrashReports = onClearCrashReports,
                onBack = { navigate(MainSection.CHATS) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatsWorkspaceScreen(
    state: FerventioUiState,
    interactiveChatState: InteractiveChatOverlayState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    onOpenMentions: () -> Unit,
    onOpenSearch: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showAddChannel by rememberSaveable { mutableStateOf(false) }
    var channelActionsId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChatModes by rememberSaveable { mutableStateOf(false) }
    var showChatUsers by rememberSaveable { mutableStateOf(false) }
    var showActionSearch by rememberSaveable { mutableStateOf(false) }
    var showMainMenu by rememberSaveable { mutableStateOf(false) }
    val workspaceStrings = rememberAppResourceStrings(state.appLanguage)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val openChannelDrawer: () -> Unit = {
        hideKeyboard()
        drawerScope.launch { drawerState.open() }
    }
    val activeTab = state.workspaceLayout.activeTab
    val activeSplitId = activeTab?.activeSplitId
    val activeChannelId = activeTab
        ?.splits
        ?.firstOrNull { split -> split.id == activeSplitId }
        ?.channelId
        ?: state.selectedChannelId
    val activeChannel = state.channels.firstOrNull { channel -> channel.id == activeChannelId }
    val selectedChannel = state.selectedChannel
    val selectedTitle = activeChannel?.let { channel ->
        state.channelTabTitles[channel.id]?.takeIf(String::isNotBlank) ?: channel.displayName
    } ?: "Чаты"
    val selectedAttention = activeChannelId?.let(state.channelAttention::get)

    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ChannelDrawerContent(
                channels = state.channels,
                selectedChannelId = state.selectedChannelId,
                pinnedChannelIds = state.pinnedChannelIds,
                tabTitles = state.channelTabTitles,
                attention = state.channelAttention,
                onDismiss = { drawerScope.launch { drawerState.close() } },
                onAddChannel = {
                    drawerScope.launch { drawerState.close() }
                    showAddChannel = true
                },
                onSelectChannel = { channelId ->
                    val activeSplitId = state.workspaceLayout.activeTab?.activeSplitId
                    if (activeSplitId != null && (state.workspaceLayout.activeTab?.splits?.size ?: 0) > 1) {
                        controller.setChatSplitChannel(activeSplitId, channelId)
                    } else {
                        controller.selectChannel(channelId)
                    }
                    drawerScope.launch { drawerState.close() }
                },
                onManageChannel = { channelId ->
                    drawerScope.launch { drawerState.close() }
                    channelActionsId = channelId
                },
                onMoveChannel = controller::moveChannel,
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(46.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = openChannelDrawer,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = localizedString("Каналы"))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .combinedClickable(
                                    onClick = openChannelDrawer,
                                    onLongClick = {
                                        hideKeyboard()
                                        selectedChannel?.id?.let { channelActionsId = it }
                                    },
                                ),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VerbatimText(
                                    text = if (selectedChannel == null) "Ferventio" else "#$selectedTitle",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if ((selectedAttention?.mentionCount ?: 0) > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge { VerbatimText(compactCount(selectedAttention?.mentionCount ?: 0)) }
                                } else if ((selectedAttention?.unreadCount ?: 0) > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge { VerbatimText(compactCount(selectedAttention?.unreadCount ?: 0)) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(6.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = when (state.connectionStatus) {
                                        io.ferventio.app.domain.ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiary
                                        io.ferventio.app.domain.ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                ) {}
                                Spacer(Modifier.width(5.dp))
                                LocalizedText(
                                    text = when (state.connectionStatus) {
                                        io.ferventio.app.domain.ConnectionStatus.CONNECTED -> "чат подключён"
                                        io.ferventio.app.domain.ConnectionStatus.CONNECTING,
                                        io.ferventio.app.domain.ConnectionStatus.WAITING_WELCOME,
                                        io.ferventio.app.domain.ConnectionStatus.CREATING_SUBSCRIPTIONS -> "подключение…"
                                        io.ferventio.app.domain.ConnectionStatus.RECONNECTING -> "переподключение…"
                                        io.ferventio.app.domain.ConnectionStatus.FAILED -> "ошибка соединения"
                                        io.ferventio.app.domain.ConnectionStatus.DISCONNECTED -> "не подключено"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        val canModerateSelected = activeChannelId
                            ?.let(state.moderatedChannelIds::contains) == true
                        IconButton(
                            onClick = {
                                hideKeyboard()
                                onOpenSearch(activeChannelId)
                            },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = workspaceStrings.string(R.string.ferventio_workspace_search_chat),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (activeChannel != null) {
                                    hideKeyboard()
                                    showChatUsers = true
                                }
                            },
                            enabled = activeChannel != null,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(Icons.Default.People, contentDescription = localizedString("Пользователи чата"))
                        }
                        if (canModerateSelected) {
                            IconButton(
                                onClick = {
                                    hideKeyboard()
                                    activeChannelId?.let(controller::selectModerationChannel)
                                    showChatModes = true
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = localizedString("Режимы чата"))
                            }
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    hideKeyboard()
                                    showMainMenu = true
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = localizedString("Меню"))
                            }
                            DropdownMenu(
                                expanded = showMainMenu,
                                onDismissRequest = { showMainMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            LocalizedText("Упоминания")
                                            if (state.mentionUnreadCount > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Badge { VerbatimText(compactCount(state.mentionUnreadCount)) }
                                            }
                                        }
                                    },
                                    onClick = {
                                        showMainMenu = false
                                        onOpenMentions()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { LocalizedText(workspaceStrings.string(R.string.ferventio_workspace_actions)) },
                                    onClick = {
                                        showMainMenu = false
                                        showActionSearch = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { LocalizedText("Настройки") },
                                    onClick = {
                                        showMainMenu = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (state.channels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LocalizedText("Пока нет каналов", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { showAddChannel = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                LocalizedText("Добавить канал")
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val useWideLayout =
                            (maxWidth >= 600.dp || maxWidth > maxHeight) && (activeTab?.splits?.size ?: 0) > 1
                        if (useWideLayout) {
                            WideSplitLayout(
                                state = state,
                                interactiveChatState = interactiveChatState,
                                controller = controller,
                                tab = activeTab ?: WorkspaceTab.default(state.selectedChannelId),
                                hideKeyboard = hideKeyboard,
                            )
                        } else {
                            PhoneChannelPager(
                                state = state,
                                interactiveChatState = interactiveChatState,
                                controller = controller,
                                hideKeyboard = hideKeyboard,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddChannel) {
        AddChannelDialog(
            onDismiss = { showAddChannel = false },
            onAdd = { login ->
                controller.addChannel(login)
                showAddChannel = false
            },
        )
    }

    if (showActionSearch) {
        val currentTab = state.workspaceLayout.activeTab
        GlobalActionSearchSheet(
            state = state,
            activeChannelId = activeChannelId,
            onDismiss = { showActionSearch = false },
            onAction = { action ->
                when {
                    action.id == "navigation:settings" -> onOpenSettings()
                    action.id == "navigation:add-channel" -> showAddChannel = true
                    action.id == "navigation:reconnect" -> controller.reconnectEventSub()
                    action.id.startsWith("channel:") -> {
                        val channelId = action.id.substringAfter("channel:")
                        if (activeSplitId != null && (currentTab?.splits?.size ?: 0) > 1) {
                            controller.setChatSplitChannel(activeSplitId, channelId)
                        } else {
                            controller.selectChannel(channelId)
                        }
                    }
                    action.id.startsWith("command:") -> {
                        activeChannelId?.let { channelId ->
                            controller.updateDraft(
                                channelId,
                                "/${action.id.substringAfter("command:")} ",
                            )
                        }
                    }
                }
            },
        )
    }

    if (state.userCard.isLoading || state.userCard.data != null || state.userCard.errorMessage != null) {
        UserCardSheet(
            cardState = state.userCard,
            isAuthenticated = state.isAuthenticated,
            showTimestamp = state.showTimestamps,
            showBadges = state.showBadges,
            showDeletedMessageContent = state.showDeletedMessageContent,
            animateEmotes = state.animateEmotes,
            emoteScalePercent = state.emoteScalePercent,
            messageDensity = state.messageDensity,
            chatNameStyle = state.chatNameStyle,
            wrapMessageLines = state.wrapMessageLines,
            mentionColorArgb = state.mentionColorArgb,
            badgeAssetsByChannel = state.badgeAssetsByChannel,
            frankerFaceZBadgesByUserId = state.frankerFaceZBadgesByUserId,
            frankerFaceZChannelBadgesByChannel = state.frankerFaceZChannelBadgesByChannel,
            cheermoteAssetsByChannel = state.cheermoteAssetsByChannel,
            emoteCatalogByChannel = state.emoteCatalogByChannel,
            timeoutPresetsSeconds = state.userCardTimeoutPresetsSeconds,
            showBanAction = state.userCardShowBanAction,
            moderationActionOrder = state.userCardModerationActionOrder,
            appLanguage = state.appLanguage,
            onDismiss = controller::closeUserCard,
            onReply = controller::replyFromUserCard,
            onMention = controller::mentionFromUserCard,
            onTimeout = controller::timeoutUser,
            onBan = controller::banUser,
            onUnban = controller::unbanUser,
            onWarn = controller::warnUser,
            onBlock = controller::blockUser,
        )
    }

    if (showChatModes) {
        activeChannelId?.let { channelId ->
            ChatModesBottomSheet(
                channelId = channelId,
                state = state.moderation,
                controller = controller,
                onDismiss = { showChatModes = false },
            )
        }
    }

    if (showChatUsers) {
        activeChannelId?.let { channelId ->
            ChatUsersBottomSheet(
                channelId = channelId,
                state = state.moderation,
                canModerate = channelId in state.moderatedChannelIds,
                isOwner = state.session?.userId == channelId,
                controller = controller,
                onDismiss = { showChatUsers = false },
            )
        }
    }

    channelActionsId
        ?.let { id -> state.channels.firstOrNull { it.id == id } }
        ?.let { channel ->
            ChannelActionsSheet(
                channel = channel,
                tabTitle = state.channelTabTitles[channel.id],
                pinned = channel.id in state.pinnedChannelIds,
                onDismiss = { channelActionsId = null },
                onTogglePinned = {
                    controller.togglePinnedChannel(channel.id)
                    channelActionsId = null
                },
                onRename = {
                    channelActionsId = null
                    renameChannelId = channel.id
                },
                onDelete = {
                    channelActionsId = null
                    deleteChannelId = channel.id
                },
            )
        }

    renameChannelId
        ?.let { id -> state.channels.firstOrNull { it.id == id } }
        ?.let { channel ->
            RenameChannelTabDialog(
                channel = channel,
                currentTitle = state.channelTabTitles[channel.id].orEmpty(),
                onDismiss = { renameChannelId = null },
                onSave = { title ->
                    controller.renameChannelTab(channel.id, title)
                    renameChannelId = null
                },
            )
        }

    deleteChannelId
        ?.let { id -> state.channels.firstOrNull { it.id == id } }
        ?.let { channel ->
            DeleteChannelDialog(
                channel = channel,
                onDismiss = { deleteChannelId = null },
                onConfirm = {
                    controller.removeChannel(channel.id)
                    deleteChannelId = null
                },
            )
        }
}
