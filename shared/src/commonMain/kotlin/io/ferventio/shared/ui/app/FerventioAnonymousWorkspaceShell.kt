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
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.attention_open
import io.ferventio.shared.generated.resources.auth_sign_in_with_twitch
import io.ferventio.shared.generated.resources.workspace_anonymous_no_channels_summary
import io.ferventio.shared.generated.resources.workspace_chats
import io.ferventio.shared.generated.resources.workspace_load_failed
import io.ferventio.shared.generated.resources.workspace_loading
import io.ferventio.shared.generated.resources.workspace_menu
import io.ferventio.shared.generated.resources.workspace_no_channels
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.workspace.WorkspaceLoadStatus
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import io.ferventio.shared.workspace.resolveWorkspaceActiveChannelId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Minimal read-only workspace surface used while no Twitch account is authorized. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioAnonymousWorkspaceShell(
    state: WorkspaceRuntimeStateHolder,
    onAuthenticate: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onAddChannel: (String) -> Unit,
    onRemoveChannel: (String) -> Unit,
    onMoveChannel: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ChatChannel, String, Modifier) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var attentionVisible by remember { mutableStateOf(false) }
    val selectedChannelId = resolveWorkspaceActiveChannelId(
        layout = state.workspaceLayout,
        selectedChannelId = state.selectedChannelId,
        channelIds = state.channelIds,
    )
    val selectedChannel = state.channels.firstOrNull { channel -> channel.id == selectedChannelId }
    val menuDescription = stringResource(Res.string.workspace_menu)
    val attentionDescription = stringResource(Res.string.attention_open)

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
                        onSetChannelPinned = { channelId, pinned ->
                            state.updatePinnedChannelIds(
                                if (pinned) {
                                    state.pinnedChannelIds + channelId
                                } else {
                                    state.pinnedChannelIds - channelId
                                },
                            )
                        },
                        onRenameChannel = state::setChannelTabTitle,
                        onRemoveChannel = onRemoveChannel,
                        onMoveChannel = onMoveChannel,
                        modifier = Modifier.weight(1f),
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
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
                        else -> content(selectedChannel, "", Modifier.fillMaxSize())
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
