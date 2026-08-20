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
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.attention_open
import io.ferventio.shared.generated.resources.auth_sign_out
import io.ferventio.shared.generated.resources.history_search_open
import io.ferventio.shared.generated.resources.notifications_enable
import io.ferventio.shared.generated.resources.notifications_enabled
import io.ferventio.shared.generated.resources.notifications_open_settings
import io.ferventio.shared.generated.resources.notifications_title
import io.ferventio.shared.generated.resources.settings_open
import io.ferventio.shared.generated.resources.workspace_chats
import io.ferventio.shared.generated.resources.workspace_load_failed
import io.ferventio.shared.generated.resources.workspace_loading
import io.ferventio.shared.generated.resources.workspace_menu
import io.ferventio.shared.generated.resources.workspace_no_channels
import io.ferventio.shared.generated.resources.workspace_no_channels_summary
import io.ferventio.shared.generated.resources.workspace_signed_in_as
import io.ferventio.shared.push.PushAuthorizationStatus
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.workspace.WorkspaceLoadStatus
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FerventioWorkspaceShell(
    state: WorkspaceRuntimeStateHolder,
    login: String?,
    onSignOut: () -> Unit = {},
    notificationAuthorizationStatus: PushAuthorizationStatus = PushAuthorizationStatus.UNKNOWN,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSaveSettings: (SharedAppPreferences) -> Unit = {},
    onUpsertHighlightRule: (HighlightRule) -> Unit = {},
    onDeleteHighlightRule: (String) -> Unit = {},
    onUpsertIgnoreRule: (IgnoreRule) -> Unit = {},
    onDeleteIgnoreRule: (String) -> Unit = {},
    onUpsertSavedFilter: (SavedMessageFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onSetChannelPinned: (String, Boolean) -> Unit = { _, _ -> },
    onRenameChannel: (String, String?) -> Unit = { _, _ -> },
    onRemoveChannel: (String) -> Unit = {},
    onMoveChannel: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    content: @Composable (ChatChannel) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var settingsVisible by remember { mutableStateOf(false) }
    var messageRulesVisible by remember { mutableStateOf(false) }
    var savedFiltersVisible by remember { mutableStateOf(false) }
    var attentionVisible by remember { mutableStateOf(false) }
    var historySearchVisible by remember { mutableStateOf(false) }
    val selectedChannel = state.channels.firstOrNull { it.id == state.selectedChannelId }
        ?: state.channels.firstOrNull()
    val menuDescription = stringResource(Res.string.workspace_menu)
    val attentionDescription = stringResource(Res.string.attention_open)
    val historySearchDescription = stringResource(Res.string.history_search_open)
    val notificationAction = notificationPermissionAction(notificationAuthorizationStatus)

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
                            state.selectChannel(channelId)
                            onSelectChannel(channelId)
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
                    Text(
                        text = stringResource(Res.string.notifications_title),
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            when (notificationAction) {
                                NotificationPermissionAction.REQUEST_PERMISSION -> onRequestNotificationPermission()
                                NotificationPermissionAction.OPEN_SETTINGS -> onOpenNotificationSettings()
                                NotificationPermissionAction.NONE -> Unit
                            }
                        },
                        enabled = notificationAction != NotificationPermissionAction.NONE,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = when (notificationAction) {
                                NotificationPermissionAction.REQUEST_PERMISSION -> stringResource(Res.string.notifications_enable)
                                NotificationPermissionAction.OPEN_SETTINGS -> stringResource(Res.string.notifications_open_settings)
                                NotificationPermissionAction.NONE -> stringResource(Res.string.notifications_enabled)
                            },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                settingsVisible = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Text(stringResource(Res.string.settings_open))
                    }
                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.auth_sign_out),
                            color = MaterialTheme.colorScheme.error,
                        )
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
                        Column {
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
                            login?.takeIf(String::isNotBlank)?.let { value ->
                                Text(
                                    text = stringResource(Res.string.workspace_signed_in_as, value),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (runtime.history != null) {
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
                            state.loadStatus == WorkspaceLoadStatus.LOADING -> WorkspaceLoadingState()
                        state.loadStatus == WorkspaceLoadStatus.FAILED && state.channels.isEmpty() -> WorkspaceFailureState()
                        selectedChannel == null -> WorkspaceEmptyState()
                        else -> content(selectedChannel)
                    }
                }
            }
        }
    }

    if (settingsVisible) {
        FerventioSettingsSheet(
            state = runtime.settings,
            notificationAuthorizationStatus = notificationAuthorizationStatus,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onSave = onSaveSettings,
            onOpenMessageRules = { messageRulesVisible = true },
            onOpenSavedFilters = { savedFiltersVisible = true },
            onDismiss = { settingsVisible = false },
        )
    }

    if (messageRulesVisible) {
        FerventioMessageRulesSheet(
            state = runtime.messageRules,
            onUpsertHighlightRule = onUpsertHighlightRule,
            onDeleteHighlightRule = onDeleteHighlightRule,
            onUpsertIgnoreRule = onUpsertIgnoreRule,
            onDeleteIgnoreRule = onDeleteIgnoreRule,
            onDismiss = { messageRulesVisible = false },
        )
    }

    if (savedFiltersVisible) {
        FerventioSavedFiltersSheet(
            state = runtime.savedFilters,
            onUpsert = onUpsertSavedFilter,
            onDelete = onDeleteSavedFilter,
            onDismiss = { savedFiltersVisible = false },
        )
    }

    if (attentionVisible) {
        FerventioAttentionSheet(
            attention = runtime.attention,
            onOpenEntry = { entry ->
                runtime.attention.requestMessageNavigation(entry.channelId, entry.messageId)
                state.selectChannel(entry.channelId)
                onSelectChannel(entry.channelId)
                attentionVisible = false
            },
            onDismiss = { attentionVisible = false },
        )
    }

    val history = runtime.history
    if (historySearchVisible && history != null) {
        FerventioHistorySearchSheet(
            history = history,
            currentChannelId = selectedChannel?.id,
            navigableChannelIds = state.channelIds.toSet(),
            onOpenMessage = { message ->
                scope.launch {
                    if (message.channelId !in state.channelIds) return@launch
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
}

@Composable
private fun WorkspaceLoadingState() {
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
private fun WorkspaceFailureState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.workspace_load_failed),
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun WorkspaceEmptyState() {
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
                text = stringResource(Res.string.workspace_no_channels_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
