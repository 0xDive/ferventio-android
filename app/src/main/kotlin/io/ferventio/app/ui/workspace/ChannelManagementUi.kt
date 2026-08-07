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
internal fun ChannelDrawerContent(
    channels: List<ChatChannel>,
    selectedChannelId: String?,
    pinnedChannelIds: List<String>,
    tabTitles: Map<String, String>,
    attention: Map<String, ChannelAttention>,
    onDismiss: () -> Unit,
    onAddChannel: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onManageChannel: (String) -> Unit,
    onMoveChannel: (String, Int) -> Unit,
) {
    val pinnedIds = remember(pinnedChannelIds) { pinnedChannelIds.toSet() }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 340.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    LocalizedText(
                        "Каналы",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    LocalizedText(
                        "Порядок совпадает со свайпами. Тяни канал за значок справа.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAddChannel) {
                    Icon(Icons.Default.Add, contentDescription = localizedString("Добавить канал"))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = localizedString("Закрыть список каналов"))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                itemsIndexed(
                    items = channels,
                    key = { _, channel -> "drawer-channel:${channel.id}" },
                    contentType = { _, _ -> "drawer-channel" },
                ) { index, channel ->
                    ChannelSwitchRow(
                        channel = channel,
                        index = index,
                        channelCount = channels.size,
                        tabTitle = tabTitles[channel.id],
                        selected = channel.id == selectedChannelId,
                        pinned = channel.id in pinnedIds,
                        attention = attention[channel.id],
                        onClick = { onSelectChannel(channel.id) },
                        onLongClick = { onManageChannel(channel.id) },
                        onMove = onMoveChannel,
                    )
                }
                if (channels.isEmpty()) {
                    item {
                        LocalizedText(
                            "Добавленных каналов пока нет",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelSwitchRow(
    channel: ChatChannel,
    index: Int,
    channelCount: Int,
    tabTitle: String?,
    selected: Boolean,
    pinned: Boolean,
    attention: ChannelAttention?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 34.dp.toPx() } }
    val latestIndex by rememberUpdatedState(index)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 7.dp, bottom = 7.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                VerbatimText(
                    tabTitle?.takeIf(String::isNotBlank) ?: "#${channel.displayName}",
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
                if (!tabTitle.isNullOrBlank()) {
                    LocalizedText(
                        "Twitch: #${verbatimArgument(channel.displayName)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if ((attention?.unreadCount ?: 0) > 0) {
                    LocalizedText(
                        "${attention?.unreadCount} непрочитанных · ${attention?.mentionCount ?: 0} mentions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                Icons.Default.DragHandle,
                contentDescription = localizedString("Изменить порядок канала"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(38.dp)
                    .padding(7.dp)
                    .pointerInput(channel.id, channelCount, dragThresholdPx) {
                        var accumulatedDrag = 0f
                        var dragIndex = index
                        detectVerticalDragGestures(
                            onDragStart = {
                                accumulatedDrag = 0f
                                dragIndex = latestIndex
                            },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount
                                while (accumulatedDrag >= dragThresholdPx && dragIndex < channelCount - 1) {
                                    dragIndex += 1
                                    onMove(channel.id, dragIndex)
                                    accumulatedDrag -= dragThresholdPx
                                }
                                while (accumulatedDrag <= -dragThresholdPx && dragIndex > 0) {
                                    dragIndex -= 1
                                    onMove(channel.id, dragIndex)
                                    accumulatedDrag += dragThresholdPx
                                }
                            },
                        )
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelActionsSheet(
    channel: ChatChannel,
    tabTitle: String?,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VerbatimText(
                tabTitle?.takeIf(String::isNotBlank) ?: "#${channel.displayName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            LocalizedText(
                "Twitch-канал #${verbatimArgument(channel.login)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChannelActionRow(
                icon = Icons.Default.PushPin,
                title = if (pinned) "Открепить канал" else "Закрепить канал",
                onClick = onTogglePinned,
            )
            ChannelActionRow(
                icon = Icons.Default.Edit,
                title = "Изменить локальное название",
                onClick = onRename,
            )
            ChannelActionRow(
                icon = Icons.Default.Delete,
                title = "Удалить канал",
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun ChannelActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            VerbatimText(title, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun RenameChannelTabDialog(
    channel: ChatChannel,
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by rememberSaveable(channel.id, currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText("Локальное название канала") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(32) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { LocalizedText("Локальное название") },
                    singleLine = true,
                )
                LocalizedText(
                    "Оставь поле пустым, чтобы снова показывать #${verbatimArgument(channel.displayName)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title) }) { LocalizedText("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { LocalizedText("Отмена") }
        },
    )
}

@Composable
internal fun DeleteChannelDialog(
    channel: ChatChannel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText("Удалить #${verbatimArgument(channel.displayName)}?") },
        text = {
            LocalizedText("Канал исчезнет из открытых чатов. Локальная история останется на устройстве и восстановится при повторном добавлении канала.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                LocalizedText("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { LocalizedText("Отмена") }
        },
    )
}
