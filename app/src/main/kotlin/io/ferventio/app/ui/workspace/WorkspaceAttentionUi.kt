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
import androidx.compose.material3.Text
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
internal fun MentionsScreen(
    state: FerventioUiState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenEntry: (AttentionEntry) -> Unit,
) {
    val entries = state.attentionEntries
    SimpleListScaffold(
        title = "Упоминания",
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        actions = {
            if (state.mentionUnreadCount > 0) {
                TextButton(onClick = controller::markAllMentionsRead) { Text("Прочитать все") }
            }
        },
    ) {
        if (entries.isEmpty()) {
            item { EmptySection("Упоминаний и highlights пока нет") }
        } else {
            items(entries, key = { "attention:${it.messageId}" }) { entry ->
                AttentionResultRow(
                    state = state,
                    entry = entry,
                    onClick = { onOpenEntry(entry) },
                )
            }
        }
    }
}

@Composable
internal fun AttentionResultRow(
    state: FerventioUiState,
    entry: AttentionEntry,
    onClick: () -> Unit,
) {
    val channel = state.channels.firstOrNull { channel ->
        channel.id == entry.channelId || channel.login.equals(entry.channelLogin, ignoreCase = true)
    }
    val accent = entry.highlightColorArgb?.let { colorFromArgb(it) }
        ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (entry.isRead) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        } else {
            accent.copy(alpha = 0.16f)
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 82.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${channel?.displayName ?: entry.channelLogin}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        entry.authorDisplayName,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        ATTENTION_TIMESTAMP_FORMATTER.format(
                            java.time.Instant.ofEpochMilli(entry.timestampMillis),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(entry.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val labels = buildList {
                    if (entry.isDirectMention) add("Mention")
                    addAll(entry.highlightReasons)
                }.distinct()
                if (labels.isNotEmpty()) {
                    Text(
                        labels.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


internal enum class SearchTypePreset(val title: String, val types: Set<ChatMessageType>) {
    ALL("Все типы", emptySet()),
    CHAT("Чат", setOf(ChatMessageType.CHAT, ChatMessageType.ACTION)),
    EVENTS(
        "События",
        setOf(
            ChatMessageType.ANNOUNCEMENT,
            ChatMessageType.SUBSCRIPTION,
            ChatMessageType.RESUBSCRIPTION,
            ChatMessageType.GIFT_SUBSCRIPTION,
            ChatMessageType.RAID,
            ChatMessageType.CHEER,
            ChatMessageType.REWARD,
        ),
    ),
    MODERATION("Модерация", setOf(ChatMessageType.MODERATION)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: FerventioUiState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenMessage: (ChatMessage) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(ChatSearchScope.ALL_CHANNELS) }
    var dateRange by rememberSaveable { mutableStateOf(ChatSearchDateRange.ALL) }
    var typePreset by rememberSaveable { mutableStateOf(SearchTypePreset.ALL) }
    var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var contextTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var contextMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isContextLoading by remember { mutableStateOf(false) }
    var contextError by remember { mutableStateOf<String?>(null) }

    val trimmed = query.trim()
    val parseError = remember(trimmed) {
        ChatSearchParser.parse(trimmed).exceptionOrNull()?.message
    }
    val hasSearchCriteria = trimmed.isNotBlank() ||
        dateRange != ChatSearchDateRange.ALL || typePreset != SearchTypePreset.ALL

    LaunchedEffect(trimmed, scope, dateRange, typePreset, state.selectedChannelId) {
        searchError = null
        if (!hasSearchCriteria || parseError != null) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        delay(250)
        isLoading = true
        controller.searchLocalHistory(
            ChatSearchRequest(
                rawQuery = trimmed,
                scope = scope,
                currentChannelId = state.selectedChannelId,
                dateRange = dateRange,
                messageTypes = typePreset.types,
            ),
        ).fold(
            onSuccess = {
                results = it
                searchError = null
            },
            onFailure = {
                results = emptyList()
                searchError = it.message ?: "Не удалось выполнить поиск"
            },
        )
        isLoading = false
    }

    LaunchedEffect(contextTarget?.id) {
        val target = contextTarget ?: return@LaunchedEffect
        isContextLoading = true
        contextError = null
        contextMessages = emptyList()
        controller.loadSearchContext(target.id, radius = 3).fold(
            onSuccess = { contextMessages = it },
            onFailure = { contextError = it.message ?: "Не удалось загрузить контекст" },
        )
        isContextLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к чатам")
                    }
                },
                title = { Text("Поиск", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Текст или поисковые операторы") },
                placeholder = { Text("from:user has:link is:deleted") },
                supportingText = {
                    Text(parseError ?: "from:  in:  has:link  is:sub  is:timeout  regex:\"…\"  type:  after:  before:")
                },
                isError = parseError != null,
                singleLine = true,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = scope == ChatSearchScope.ALL_CHANNELS,
                        onClick = { scope = ChatSearchScope.ALL_CHANNELS },
                        label = { Text("Все каналы") },
                    )
                }
                item {
                    FilterChip(
                        selected = scope == ChatSearchScope.CURRENT_CHANNEL,
                        onClick = { scope = ChatSearchScope.CURRENT_CHANNEL },
                        enabled = state.selectedChannelId != null,
                        label = { Text("Текущий канал") },
                    )
                }
                items(ChatSearchDateRange.entries) { range ->
                    FilterChip(
                        selected = dateRange == range,
                        onClick = { dateRange = range },
                        label = {
                            Text(
                                when (range) {
                                    ChatSearchDateRange.ALL -> "За всё время"
                                    ChatSearchDateRange.LAST_24_HOURS -> "24 часа"
                                    ChatSearchDateRange.LAST_7_DAYS -> "7 дней"
                                    ChatSearchDateRange.LAST_30_DAYS -> "30 дней"
                                },
                            )
                        },
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SearchTypePreset.entries) { preset ->
                    FilterChip(
                        selected = typePreset == preset,
                        onClick = { typePreset = preset },
                        label = { Text(preset.title) },
                    )
                }
            }

            when {
                !state.localHistoryEnabled -> Text(
                    "Сохранение новых сообщений выключено. Поиск выполняется по уже существующей базе.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    parseError != null -> item { EmptySection(parseError) }
                    searchError != null -> item { EmptySection(searchError.orEmpty()) }
                    !hasSearchCriteria -> item {
                        EmptySection("Введи текст или выбери период/тип. Поиск работает по всей Room-истории.")
                    }
                    isLoading && results.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    results.isEmpty() -> item { EmptySection("Ничего не найдено") }
                    else -> items(results, key = { "search:${it.channelId}:${it.id}" }) { message ->
                        MessageResultRow(
                            state = state,
                            message = message,
                            onOpen = { onOpenMessage(message) },
                            onContext = { contextTarget = message },
                            onOpenUser = { controller.openSearchUserCard(message) },
                        )
                    }
                }
            }
        }
    }

    contextTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { contextTarget = null },
            title = { Text("Контекст сообщения") },
            text = {
                when {
                    isContextLoading -> Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    contextError != null -> Text(contextError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    contextMessages.isEmpty() -> Text("Контекст не найден")
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(contextMessages, key = { "context:${it.id}" }) { message ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        contextTarget = null
                                        onOpenMessage(message)
                                    },
                                color = if (message.id == target.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(
                                        message.userDisplayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(message.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    contextTarget = null
                    onOpenMessage(target)
                }) { Text("Перейти") }
            },
            dismissButton = {
                TextButton(onClick = { contextTarget = null }) { Text("Закрыть") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimpleListScaffold(
    title: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к чатам")
                    }
                },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                actions = { actions() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
internal fun MessageResultRow(
    state: FerventioUiState,
    message: ChatMessage,
    onOpen: () -> Unit,
    onContext: () -> Unit,
    onOpenUser: () -> Unit,
) {
    val channel = state.channels.firstOrNull { it.id == message.channelId }
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = if (message.isDeleted) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${channel?.displayName ?: message.channelLogin}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    message.userDisplayName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ATTENTION_TIMESTAMP_FORMATTER.format(java.time.Instant.ofEpochMilli(message.timestampMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия результата")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Показать контекст") },
                            onClick = {
                                menuExpanded = false
                                onContext()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Карточка пользователя и модерация") },
                            onClick = {
                                menuExpanded = false
                                onOpenUser()
                            },
                        )
                    }
                }
            }
            Text(message.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val metadata = buildList {
                add(message.type.name.lowercase())
                if (message.isDeleted) add("удалено")
                message.moderation.action?.let { action -> add(action.name.lowercase()) }
            }
            Text(
                metadata.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EmptySection(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun compactCount(value: Int): String = when {
    value > 999 -> "999+"
    else -> value.toString()
}


internal val ATTENTION_TIMESTAMP_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
