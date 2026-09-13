package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.attention_open
import io.ferventio.shared.generated.resources.auth_sign_in_with_twitch
import io.ferventio.shared.generated.resources.history_search_open
import io.ferventio.shared.generated.resources.saved_filters_title
import io.ferventio.shared.generated.resources.settings_open
import io.ferventio.shared.generated.resources.workspace_anonymous_no_channels_summary
import io.ferventio.shared.generated.resources.workspace_chats
import io.ferventio.shared.generated.resources.workspace_load_failed
import io.ferventio.shared.generated.resources.workspace_loading
import io.ferventio.shared.generated.resources.workspace_menu
import io.ferventio.shared.generated.resources.workspace_no_channels
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.workspace.WorkspaceLoadStatus
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import io.ferventio.shared.workspace.activeWorkspaceSplitIdForChannelSelection
import io.ferventio.shared.workspace.resolveWorkspaceActiveChannelId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Read-only workspace surface used while no Twitch account is authorized. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioAnonymousWorkspaceShell(
    state: WorkspaceRuntimeStateHolder,
    onAuthenticate: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onAddChannel: (String) -> Unit,
    onSetChannelPinned: (String, Boolean) -> Unit,
    onRenameChannel: (String, String?) -> Unit,
    onRemoveChannel: (String) -> Unit,
    onMoveChannel: (String, Int) -> Unit,
    onSaveHistoryPreferences: (SharedAppPreferences) -> Unit = {},
    onUpsertSavedFilter: (SavedMessageFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
    onImportSavedFilters: (String) -> Unit = {},
    onSetSplitFilterQuery: (String, String) -> Unit = { _, _ -> },
    onSetSplitChannel: (String, String) -> Unit = { _, _ -> },
    onFocusSplit: (String) -> Unit = {},
    onAddSplit: () -> Unit = {},
    onRemoveSplit: (String) -> Unit = {},
    onSetPrimaryFraction: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var attentionVisible by remember { mutableStateOf(false) }
    var historySearchVisible by remember { mutableStateOf(false) }
    var historySettingsVisible by remember { mutableStateOf(false) }
    var savedFiltersVisible by remember { mutableStateOf(false) }
    val selectedChannelId = resolveWorkspaceActiveChannelId(
        layout = state.workspaceLayout,
        selectedChannelId = state.selectedChannelId,
        channelIds = state.channelIds,
    )
    val selectedChannel = state.channels.firstOrNull { channel -> channel.id == selectedChannelId }
    val selectedCanonicalChannelId = selectedChannel?.id
        ?.takeUnless { channelId -> channelId.startsWith("anonymous:") }
    val canonicalChannelIds = state.channelIds
        .filterNot { channelId -> channelId.startsWith("anonymous:") }
        .toSet()
    val menuDescription = stringResource(Res.string.workspace_menu)
    val attentionDescription = stringResource(Res.string.attention_open)
    val historySearchDescription = stringResource(Res.string.history_search_open)

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxSize()) {
                    WorkspaceChannelManagement(
                        state = state,
                        selectedChannel = selectedChannel,
                        onSelectChannel = { channelId ->
                            val splitId = activeWorkspaceSplitIdForChannelSelection(state.workspaceLayout)
                            state.selectChannel(channelId)
                            if (splitId != null) {
                                onSetSplitChannel(splitId, channelId)
                            } else {
                                onSelectChannel(channelId)
                            }
                            scope.launch { drawerState.close() }
                        },
                        onAddChannel = onAddChannel,
                        onSetChannelPinned = onSetChannelPinned,
                        onRenameChannel = onRenameChannel,
                        onRemoveChannel = onRemoveChannel,
                        onMoveChannel = onMoveChannel,
                        modifier = Modifier.weight(1f),
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                savedFiltersVisible = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Text(stringResource(Res.string.saved_filters_title))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                historySettingsVisible = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Text(stringResource(Res.string.settings_open))
                    }
                    TextButton(
                        onClick = onAuthenticate,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(Res.string.auth_sign_in_with_twitch))
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        TextButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.semantics { contentDescription = menuDescription },
                        ) {
                            Text(text = "☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    title = {
                        Text(
                            text = selectedChannel?.let { channel ->
                                state.channelTabTitles[channel.id]?.takeIf(String::isNotBlank)
                                    ?: "#${channel.displayName}"
                            } ?: stringResource(Res.string.workspace_chats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (runtime.history != null && selectedCanonicalChannelId != null) {
                            TextButton(
                                onClick = { historySearchVisible = true },
                                modifier = Modifier.semantics {
                                    contentDescription = historySearchDescription
                                },
                            ) {
                                Text(
                                    text = "⌕",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(
                            onClick = { attentionVisible = true },
                            modifier = Modifier.semantics { contentDescription = attentionDescription },
                        ) {
                            val unreadMentions = runtime.attention.mentionUnreadCount
                            Text(
                                text = if (unreadMentions > 0) {
                                    "@${unreadMentions.coerceAtMost(999)}"
                                } else {
                                    "@"
                                },
                                fontWeight = if (unreadMentions > 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (unreadMentions > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (state.loadStatus == WorkspaceLoadStatus.FAILED && state.channels.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(Res.string.workspace_load_failed),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        state.loadStatus == WorkspaceLoadStatus.IDLE ||
                            state.loadStatus == WorkspaceLoadStatus.LOADING -> AnonymousWorkspaceLoadingState()
                        state.loadStatus == WorkspaceLoadStatus.FAILED && state.channels.isEmpty() ->
                            AnonymousWorkspaceFailureState(onAuthenticate)
                        selectedChannel == null -> AnonymousWorkspaceEmptyState(onAuthenticate)
                        else -> FerventioWorkspaceResponsiveContent(
                            state = state,
                            savedFilters = runtime.savedFilters.filters,
                            decorations = runtime.messageRules.decorationsByMessageId,
                            onSetSplitFilterQuery = onSetSplitFilterQuery,
                            onSetSplitChannel = onSetSplitChannel,
                            onFocusSplit = onFocusSplit,
                            onAddSplit = onAddSplit,
                            onRemoveSplit = onRemoveSplit,
                            onSetPrimaryFraction = onSetPrimaryFraction,
                            modifier = Modifier.fillMaxSize(),
                            content = content,
                        )
                    }
                }
            }
        }
    }

    if (attentionVisible) {
        FerventioAttentionSheet(
            attention = runtime.attention,
            onOpenEntry = { entry ->
                if (entry.channelId in state.channelIds) {
                    runtime.attention.requestMessageNavigation(entry.channelId, entry.messageId)
                    state.selectChannel(entry.channelId)
                    onSelectChannel(entry.channelId)
                }
                attentionVisible = false
            },
            onDismiss = { attentionVisible = false },
        )
    }

    val history = runtime.history
    if (historySearchVisible && history != null) {
        FerventioHistorySearchSheet(
            history = history,
            currentChannelId = selectedCanonicalChannelId,
            navigableChannelIds = canonicalChannelIds,
            onOpenMessage = { message ->
                scope.launch {
                    if (message.channelId !in canonicalChannelIds) return@launch
                    val contextMessages = runCatching {
                        history.loadMessageContext(message.id)
                    }.getOrDefault(emptyList()).ifEmpty { listOf(message) }
                    runtime.chat.prependHistory(message.channelId, contextMessages)
                    runtime.attention.requestMessageNavigation(message.channelId, message.id)
                    state.selectChannel(message.channelId)
                    onSelectChannel(message.channelId)
                    historySearchVisible = false
                }
            },
            onDismiss = { historySearchVisible = false },
        )
    }

    if (savedFiltersVisible) {
        FerventioSavedFiltersSheet(
            state = runtime.savedFilters,
            onUpsert = onUpsertSavedFilter,
            onDelete = onDeleteSavedFilter,
            onImport = onImportSavedFilters,
            onDismiss = { savedFiltersVisible = false },
        )
    }

    if (historySettingsVisible) {
        FerventioAnonymousHistorySettingsSheet(
            state = runtime.settings,
            onSave = onSaveHistoryPreferences,
            onDismiss = { historySettingsVisible = false },
        )
    }
}

@Composable
private fun AnonymousWorkspaceLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.workspace_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnonymousWorkspaceFailureState(onAuthenticate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.workspace_load_failed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onAuthenticate) {
                Text(stringResource(Res.string.auth_sign_in_with_twitch))
            }
        }
    }
}

@Composable
private fun AnonymousWorkspaceEmptyState(onAuthenticate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.workspace_no_channels),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.workspace_anonymous_no_channels_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAuthenticate) {
                Text(stringResource(Res.string.auth_sign_in_with_twitch))
            }
        }
    }
}
