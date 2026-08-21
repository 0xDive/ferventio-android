package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.MAX_SPLITS_PER_TAB
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.SplitLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.workspace_split_add
import io.ferventio.shared.generated.resources.workspace_split_choose_channel
import io.ferventio.shared.generated.resources.workspace_split_filter_action
import io.ferventio.shared.generated.resources.workspace_split_remove
import io.ferventio.shared.generated.resources.workspace_split_unassigned
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import org.jetbrains.compose.resources.stringResource

/** Android-parity responsive workspace content: compact channel view or wide split layout. */
@Composable
internal fun FerventioWorkspaceResponsiveContent(
    state: WorkspaceRuntimeStateHolder,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    onSetSplitFilterQuery: (String, String) -> Unit,
    onSetSplitChannel: (String, String) -> Unit,
    onFocusSplit: (String) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    onSetPrimaryFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    val selectedChannel = state.channels.firstOrNull { it.id == state.selectedChannelId }
        ?: state.channels.firstOrNull()
    val activeTab = state.workspaceLayout.activeTab
    val splits = activeTab?.splits.orEmpty().take(MAX_SPLITS_PER_TAB)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useWideLayout =
            (maxWidth >= 600.dp || maxWidth > maxHeight) && splits.size > 1
        if (!useWideLayout) {
            selectedChannel?.let { channel ->
                content(channel, "", Modifier.fillMaxSize())
            }
        } else {
            WideWorkspaceSplitLayout(
                state = state,
                tab = activeTab ?: WorkspaceTab.default(state.selectedChannelId),
                savedFilters = savedFilters,
                decorations = decorations,
                onSetSplitFilterQuery = onSetSplitFilterQuery,
                onSetSplitChannel = onSetSplitChannel,
                onFocusSplit = onFocusSplit,
                onAddSplit = onAddSplit,
                onRemoveSplit = onRemoveSplit,
                onSetPrimaryFraction = onSetPrimaryFraction,
                content = content,
            )
        }
    }
}

@Composable
private fun WideWorkspaceSplitLayout(
    state: WorkspaceRuntimeStateHolder,
    tab: WorkspaceTab,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    onSetSplitFilterQuery: (String, String) -> Unit,
    onSetSplitChannel: (String, String) -> Unit,
    onFocusSplit: (String) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    onSetPrimaryFraction: (Float) -> Unit,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    val splits = tab.splits.take(MAX_SPLITS_PER_TAB)
    when (splits.size) {
        0 -> Unit
        1 -> WorkspaceSplitPane(
            state = state,
            split = splits.first(),
            splitCount = 1,
            activeSplitId = tab.activeSplitId,
            savedFilters = savedFilters,
            decorations = decorations,
            onSetSplitFilterQuery = onSetSplitFilterQuery,
            onSetSplitChannel = onSetSplitChannel,
            onFocusSplit = onFocusSplit,
            onAddSplit = onAddSplit,
            onRemoveSplit = onRemoveSplit,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
        2 -> ResizableSplitColumns(
            state = state,
            left = listOf(splits[0]),
            right = listOf(splits[1]),
            splitCount = 2,
            activeSplitId = tab.activeSplitId,
            fraction = tab.primaryFraction,
            savedFilters = savedFilters,
            decorations = decorations,
            onSetSplitFilterQuery = onSetSplitFilterQuery,
            onSetSplitChannel = onSetSplitChannel,
            onFocusSplit = onFocusSplit,
            onAddSplit = onAddSplit,
            onRemoveSplit = onRemoveSplit,
            onSetPrimaryFraction = onSetPrimaryFraction,
            content = content,
        )
        else -> ResizableSplitColumns(
            state = state,
            left = splits.filterIndexed { index, _ -> index % 2 == 0 },
            right = splits.filterIndexed { index, _ -> index % 2 == 1 },
            splitCount = splits.size,
            activeSplitId = tab.activeSplitId,
            fraction = tab.primaryFraction,
            savedFilters = savedFilters,
            decorations = decorations,
            onSetSplitFilterQuery = onSetSplitFilterQuery,
            onSetSplitChannel = onSetSplitChannel,
            onFocusSplit = onFocusSplit,
            onAddSplit = onAddSplit,
            onRemoveSplit = onRemoveSplit,
            onSetPrimaryFraction = onSetPrimaryFraction,
            content = content,
        )
    }
}

@Composable
private fun ResizableSplitColumns(
    state: WorkspaceRuntimeStateHolder,
    left: List<SplitLayout>,
    right: List<SplitLayout>,
    splitCount: Int,
    activeSplitId: String?,
    fraction: Float,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    onSetSplitFilterQuery: (String, String) -> Unit,
    onSetSplitChannel: (String, String) -> Unit,
    onFocusSplit: (String) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    onSetPrimaryFraction: (Float) -> Unit,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    var widthPx by remember { mutableStateOf(1) }
    var localFraction by remember { mutableStateOf(fraction.coerceIn(0.25f, 0.75f)) }
    LaunchedEffect(fraction) {
        localFraction = fraction.coerceIn(0.25f, 0.75f)
    }

    Row(
        modifier = Modifier.fillMaxSize().onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
    ) {
        WorkspaceSplitColumn(
            state = state,
            splits = left,
            splitCount = splitCount,
            activeSplitId = activeSplitId,
            savedFilters = savedFilters,
            decorations = decorations,
            onSetSplitFilterQuery = onSetSplitFilterQuery,
            onSetSplitChannel = onSetSplitChannel,
            onFocusSplit = onFocusSplit,
            onAddSplit = onAddSplit,
            onRemoveSplit = onRemoveSplit,
            modifier = Modifier.weight(localFraction),
            content = content,
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onSetPrimaryFraction(localFraction) },
                    ) { _, dragAmount ->
                        localFraction = (
                            localFraction + dragAmount / widthPx.toFloat()
                        ).coerceIn(0.25f, 0.75f)
                    }
                },
        )
        WorkspaceSplitColumn(
            state = state,
            splits = right,
            splitCount = splitCount,
            activeSplitId = activeSplitId,
            savedFilters = savedFilters,
            decorations = decorations,
            onSetSplitFilterQuery = onSetSplitFilterQuery,
            onSetSplitChannel = onSetSplitChannel,
            onFocusSplit = onFocusSplit,
            onAddSplit = onAddSplit,
            onRemoveSplit = onRemoveSplit,
            modifier = Modifier.weight(1f - localFraction),
            content = content,
        )
    }
}

@Composable
private fun WorkspaceSplitColumn(
    state: WorkspaceRuntimeStateHolder,
    splits: List<SplitLayout>,
    splitCount: Int,
    activeSplitId: String?,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    onSetSplitFilterQuery: (String, String) -> Unit,
    onSetSplitChannel: (String, String) -> Unit,
    onFocusSplit: (String) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    modifier: Modifier,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        splits.forEach { split ->
            WorkspaceSplitPane(
                state = state,
                split = split,
                splitCount = splitCount,
                activeSplitId = activeSplitId,
                savedFilters = savedFilters,
                decorations = decorations,
                onSetSplitFilterQuery = onSetSplitFilterQuery,
                onSetSplitChannel = onSetSplitChannel,
                onFocusSplit = onFocusSplit,
                onAddSplit = onAddSplit,
                onRemoveSplit = onRemoveSplit,
                modifier = Modifier.weight(1f),
                content = content,
            )
        }
    }
}

@Composable
private fun WorkspaceSplitPane(
    state: WorkspaceRuntimeStateHolder,
    split: SplitLayout,
    splitCount: Int,
    activeSplitId: String?,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    onSetSplitFilterQuery: (String, String) -> Unit,
    onSetSplitChannel: (String, String) -> Unit,
    onFocusSplit: (String) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    modifier: Modifier,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val channel = split.channelId?.let { id -> state.channels.firstOrNull { it.id == id } }
    var channelMenuVisible by rememberSaveable(split.id) { mutableStateOf(false) }
    var filterEditorVisible by rememberSaveable(split.id) { mutableStateOf(false) }
    val active = split.id == activeSplitId

    Surface(
        modifier = modifier.padding(2.dp),
        border = BorderStroke(
            width = if (active) 1.5.dp else 0.5.dp,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = {
                            onFocusSplit(split.id)
                            channelMenuVisible = true
                        },
                    ) {
                        Text(
                            text = channel?.let { "#${it.displayName}" }
                                ?: stringResource(Res.string.workspace_split_unassigned),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                    DropdownMenu(
                        expanded = channelMenuVisible,
                        onDismissRequest = { channelMenuVisible = false },
                    ) {
                        state.channels.forEach { available ->
                            DropdownMenuItem(
                                text = { Text("#${available.displayName}") },
                                onClick = {
                                    channelMenuVisible = false
                                    onSetSplitChannel(split.id, available.id)
                                },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        onFocusSplit(split.id)
                        filterEditorVisible = true
                    },
                ) {
                    Text(stringResource(Res.string.workspace_split_filter_action))
                }
                if (active && splitCount < MAX_SPLITS_PER_TAB) {
                    TextButton(onClick = onAddSplit) {
                        Text("+")
                    }
                }
                if (splitCount > 1) {
                    TextButton(onClick = { onRemoveSplit(split.id) }) {
                        Text("×")
                    }
                }
            }

            if (channel == null) {
                TextButton(
                    onClick = { channelMenuVisible = true },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Text(stringResource(Res.string.workspace_split_choose_channel))
                }
            } else {
                content(channel, split.filterQuery, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    if (filterEditorVisible) {
        FerventioSplitFilterEditorDialog(
            initialExpression = split.filterQuery,
            savedFilters = savedFilters,
            messages = channel?.let { runtime.chat.messages(it.id) }.orEmpty(),
            decorations = decorations,
            onDismiss = { filterEditorVisible = false },
            onSave = { query ->
                onSetSplitFilterQuery(split.id, query)
                filterEditorVisible = false
            },
        )
    }
}
