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
import io.ferventio.app.application.InteractiveChatCoordinator
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.InteractiveChatOverlayState
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
fun FerventioApp(
    controller: FerventioController,
    interactiveChatCoordinator: InteractiveChatCoordinator,
    pushCoordinator: PushCoordinator,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onExportCrashReports: () -> Unit,
    onClearCrashReports: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val interactiveChatState by interactiveChatCoordinator.state.collectAsStateWithLifecycle()
    val pushState by pushCoordinator.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.pendingExternalUri) {
        val uri = state.pendingExternalUri ?: return@LaunchedEffect
        runCatching { uriHandler.openUri(uri) }
            .onFailure { controller.reportAuthorizationBrowserError(it.message) }
        controller.consumePendingExternalUri()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            controller.clearError()
        }
    }
    LaunchedEffect(pushState.errorMessage) {
        pushState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            pushCoordinator.clearError()
        }
    }

    ProvideAppResourceStrings(state.appLanguage) {
        FerventioTheme(
            themeMode = state.themeMode,
            fontScalePercent = state.fontScalePercent,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            ) {
                when {
                    state.isBootstrapping -> LoadingScreen(state.isChannelsLoading)
                    else -> AuthenticatedShell(
                        state = state,
                        interactiveChatState = interactiveChatState,
                        controller = controller,
                        snackbarHostState = snackbarHostState,
                        pushState = pushState,
                        onTestPush = pushCoordinator::sendTest,
                        onReconnectPush = pushCoordinator::reconnect,
                        onExportSettings = onExportSettings,
                        onImportSettings = onImportSettings,
                        onExportCrashReports = onExportCrashReports,
                        onClearCrashReports = onClearCrashReports,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(isChannelsLoading: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FerventioMark()
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            LocalizedText(if (isChannelsLoading) "Восстанавливаем каналы и историю…" else "Проверяем Twitch-сессию…")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: FerventioUiState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    var showAddChannel by rememberSaveable { mutableStateOf(false) }
    var showActionSearch by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    LaunchedEffect(state.userCard.isLoading, state.userCard.data) {
        if (state.userCard.isLoading || state.userCard.data != null) hideKeyboard()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        VerbatimText("Ferventio", fontWeight = FontWeight.Bold)
                        LocalizedText(
                            text = connectionLabel(state.connectionStatus),
                            style = MaterialTheme.typography.labelSmall,
                            color = connectionColor(state.connectionStatus),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hideKeyboard()
                        showActionSearch = true
                    }) {
                        Icon(Icons.Default.Tune, contentDescription = localizedString("Действия и поиск"))
                    }
                    IconButton(onClick = {
                        hideKeyboard()
                        showAddChannel = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = localizedString("Добавить канал"))
                    }
                    IconButton(onClick = {
                        hideKeyboard()
                        onOpenSettings()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = localizedString("Настройки"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
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
                if (state.isChannelsLoading) {
                    ChannelsLoading()
                } else {
                    EmptyChannels(onAddChannel = {
                        hideKeyboard()
                        showAddChannel = true
                    })
                }
            } else {
                if (state.isChannelsLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ChannelTabs(
                    channels = state.channels,
                    selectedChannelId = state.selectedChannelId,
                    onSelect = { channelId ->
                        hideKeyboard()
                        controller.selectChannel(channelId)
                    },
                )
                ChatContent(
                    state = state,
                    onSend = controller::sendMessage,
                    onDraftChange = { channelId, draft -> controller.updateDraft(channelId, draft) },
                    onRetryMessage = controller::retryOutgoingMessage,
                    onEmoteUsed = controller::recordEmoteUsage,
                    onToggleFavoriteEmote = controller::toggleFavoriteEmote,
                    onOpenUser = { message ->
                        hideKeyboard()
                        controller.openUserCard(message)
                    },
                    onScrollPositionChanged = controller::saveScrollPosition,
                    onLoadOlderHistory = controller::loadOlderHistory,
                    onUserInteraction = hideKeyboard,
                )
            }
        }
    }

    if (showAddChannel) {
        AddChannelDialog(
            onDismiss = { showAddChannel = false },
            onAdd = {
                controller.addChannel(it)
                showAddChannel = false
            },
        )
    }

    if (showActionSearch) {
        GlobalActionSearchSheet(
            state = state,
            activeChannelId = state.selectedChannelId,
            onDismiss = { showActionSearch = false },
            onAction = { action ->
                when {
                    action.id == "navigation:settings" -> onOpenSettings()
                    action.id == "navigation:add-channel" -> showAddChannel = true
                    action.id == "navigation:reconnect" -> {
                        state.selectedChannelId?.let { channelId ->
                            controller.updateDraft(channelId, "/reconnect")
                        }
                    }
                    action.id.startsWith("channel:") -> {
                        controller.selectChannel(action.id.substringAfter("channel:"))
                    }
                    action.id.startsWith("command:") -> {
                        state.selectedChannelId?.let { channelId ->
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
}

@Composable
private fun ChannelsLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            LocalizedText("Загружаем сохранённые каналы…", fontWeight = FontWeight.SemiBold)
            LocalizedText(
                "Локальный кэш появится сразу, данные Twitch обновятся в фоне.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyChannels(onAddChannel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(28.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp),
            ) {
                LocalizedText("Пока нет каналов", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                LocalizedText(
                    "Добавь Twitch-канал. Читать публичный чат можно без аккаунта; вход нужен для отправки и модерации.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onAddChannel) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    LocalizedText("Добавить канал")
                }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION") // Keep current tab geometry; migrate after visual regression coverage.
private fun ChannelTabs(
    channels: List<ChatChannel>,
    selectedChannelId: String?,
    onSelect: (String) -> Unit,
) {
    if (channels.isEmpty()) return
    if (channels.size == 1) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            VerbatimText(
                text = "#${channels.first().displayName}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val selectedIndex = channels
        .indexOfFirst { it.id == selectedChannelId }
        .takeIf { it >= 0 }
        ?.coerceIn(0, channels.lastIndex)
        ?: 0
    val channelIds = channels.map(ChatChannel::id)

    key(channelIds) {
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 8.dp,
            divider = {},
        ) {
            channels.forEachIndexed { index, channel ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(channel.id) },
                    text = {
                        VerbatimText(
                            text = "#${channel.displayName}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatContent(
    state: FerventioUiState,
    onSend: (String, String?) -> Boolean,
    onDraftChange: (String, String) -> Unit,
    onRetryMessage: (ChatMessage) -> Unit,
    onEmoteUsed: (ThirdPartyEmoteAsset) -> Unit,
    onToggleFavoriteEmote: (ThirdPartyEmoteAsset) -> Unit,
    onOpenUser: (ChatMessage) -> Unit,
    onScrollPositionChanged: (String, String?, Int, Int, Boolean) -> Unit,
    onLoadOlderHistory: (String) -> Unit,
    onUserInteraction: () -> Unit,
) {
    val channelId = state.selectedChannelId ?: return
    key(channelId) {
        ChannelChatContent(
            state = state,
            channelId = channelId,
            onSend = onSend,
            onDraftChange = { draft -> onDraftChange(channelId, draft) },
            onRetryMessage = onRetryMessage,
            onEmoteUsed = onEmoteUsed,
            onToggleFavoriteEmote = onToggleFavoriteEmote,
            onOpenUser = onOpenUser,
            onScrollPositionChanged = onScrollPositionChanged,
            onLoadOlderHistory = { onLoadOlderHistory(channelId) },
            onUserInteraction = onUserInteraction,
        )
    }
}