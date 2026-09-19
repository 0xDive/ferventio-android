package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.workspace_add
import io.ferventio.shared.generated.resources.workspace_add_channel
import io.ferventio.shared.generated.resources.workspace_add_channel_title
import io.ferventio.shared.generated.resources.workspace_cancel
import io.ferventio.shared.generated.resources.workspace_channel_limit
import io.ferventio.shared.generated.resources.workspace_channel_login
import io.ferventio.shared.generated.resources.workspace_close_channels
import io.ferventio.shared.generated.resources.workspace_channels
import io.ferventio.shared.generated.resources.workspace_confirm_remove
import io.ferventio.shared.generated.resources.workspace_local_title
import io.ferventio.shared.generated.resources.workspace_local_title_hint
import io.ferventio.shared.generated.resources.workspace_manage_channel
import io.ferventio.shared.generated.resources.workspace_move_down
import io.ferventio.shared.generated.resources.workspace_move_up
import io.ferventio.shared.generated.resources.workspace_mutation_failed
import io.ferventio.shared.generated.resources.workspace_mutation_in_progress
import io.ferventio.shared.generated.resources.workspace_pin_channel
import io.ferventio.shared.generated.resources.workspace_pinned
import io.ferventio.shared.generated.resources.workspace_remove_channel
import io.ferventio.shared.generated.resources.workspace_remove_channel_body
import io.ferventio.shared.generated.resources.workspace_remove_channel_title
import io.ferventio.shared.generated.resources.workspace_rename_channel
import io.ferventio.shared.generated.resources.workspace_save
import io.ferventio.shared.generated.resources.workspace_unpin_channel
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WorkspaceChannelManagement(
    state: WorkspaceRuntimeStateHolder,
    selectedChannel: ChatChannel?,
    onSelectChannel: (String) -> Unit,
    onAddChannel: (String) -> Unit,
    onSetChannelPinned: (String, Boolean) -> Unit,
    onRenameChannel: (String, String?) -> Unit,
    onRemoveChannel: (String) -> Unit,
    onMoveChannel: (String, Int) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val attentionState = LocalFerventioRuntimeState.current.attention
    var addDialogVisible by remember { mutableStateOf(false) }
    var managedChannelId by remember { mutableStateOf<String?>(null) }
    var renameChannelId by remember { mutableStateOf<String?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var removeChannelId by remember { mutableStateOf<String?>(null) }
    val busy = state.mutationInFlight

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.workspace_channels),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { addDialogVisible = true },
                enabled = !busy && state.channels.size < MAX_WORKSPACE_CHANNELS,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(Res.string.workspace_add_channel),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.workspace_close_channels),
                )
            }
        }

        if (state.channels.size >= MAX_WORKSPACE_CHANNELS) {
            Text(
                text = stringResource(Res.string.workspace_channel_limit),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.workspace_mutation_in_progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.mutationErrorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_mutation_failed, error),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    IconButton(onClick = state::clearMutationError) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.workspace_cancel),
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(
                items = state.channels,
                key = { _, channel -> channel.id },
            ) { index, channel ->
                val pinned = channel.id in state.pinnedChannelIds
                val title = state.channelTabTitles[channel.id]
                    ?.takeIf(String::isNotBlank)
                    ?: "#${channel.displayName}"
                val attention = attentionState.attention(channel.id)
                NavigationDrawerItem(
                    label = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (pinned) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (channel.id == selectedChannel?.id) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                            if (title != "#${channel.displayName}") {
                                Text(
                                    text = "Twitch: #${channel.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    selected = channel.id == selectedChannel?.id,
                    onClick = { onSelectChannel(channel.id) },
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (attention.mentionCount > 0) {
                                Text(
                                    text = "@${compactAttentionCount(attention.mentionCount)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else if (attention.unreadCount > 0) {
                                Text(
                                    text = compactAttentionCount(attention.unreadCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            IconButton(
                                onClick = { managedChannelId = channel.id },
                                enabled = !busy,
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(Res.string.workspace_manage_channel),
                                )
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (addDialogVisible) {
        WorkspaceAddChannelDialog(
            busy = busy,
            canAddChannel = state.channels.size < MAX_WORKSPACE_CHANNELS,
            onDismiss = { addDialogVisible = false },
            onAdd = onAddChannel,
        )
    }

    managedChannelId?.let { channelId ->
        val channel = state.channels.firstOrNull { it.id == channelId }
        if (channel == null) {
            managedChannelId = null
        } else {
            val index = state.channels.indexOfFirst { it.id == channelId }
            val pinned = channelId in state.pinnedChannelIds
            AlertDialog(
                onDismissRequest = { if (!busy) managedChannelId = null },
                title = { Text(stringResource(Res.string.workspace_manage_channel)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = state.channelTabTitles[channelId]?.takeIf(String::isNotBlank)
                                ?: "#${channel.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (pinned) {
                            Text(
                                text = stringResource(Res.string.workspace_pinned),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(
                            onClick = {
                                onSetChannelPinned(channelId, !pinned)
                                managedChannelId = null
                            },
                            enabled = !busy,
                        ) {
                            Text(
                                stringResource(
                                    if (pinned) Res.string.workspace_unpin_channel
                                    else Res.string.workspace_pin_channel,
                                ),
                            )
                        }
                        TextButton(
                            onClick = {
                                renameTitle = state.channelTabTitles[channelId].orEmpty()
                                renameChannelId = channelId
                                managedChannelId = null
                            },
                            enabled = !busy,
                        ) { Text(stringResource(Res.string.workspace_rename_channel)) }
                        Row {
                            TextButton(
                                onClick = {
                                    onMoveChannel(channelId, index - 1)
                                    managedChannelId = null
                                },
                                enabled = !busy && index > 0,
                            ) { Text(stringResource(Res.string.workspace_move_up)) }
                            TextButton(
                                onClick = {
                                    onMoveChannel(channelId, index + 1)
                                    managedChannelId = null
                                },
                                enabled = !busy && index >= 0 && index < state.channels.lastIndex,
                            ) { Text(stringResource(Res.string.workspace_move_down)) }
                        }
                        TextButton(
                            onClick = {
                                removeChannelId = channelId
                                managedChannelId = null
                            },
                            enabled = !busy,
                        ) {
                            Text(
                                text = stringResource(Res.string.workspace_remove_channel),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { managedChannelId = null }) {
                        Text(stringResource(Res.string.workspace_cancel))
                    }
                },
            )
        }
    }

    renameChannelId?.let { channelId ->
        val channel = state.channels.firstOrNull { it.id == channelId }
        if (channel == null) {
            renameChannelId = null
        } else {
            AlertDialog(
                onDismissRequest = { if (!busy) renameChannelId = null },
                title = { Text(stringResource(Res.string.workspace_local_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.workspace_local_title)) },
                            singleLine = true,
                            enabled = !busy,
                        )
                        Text(
                            text = stringResource(Res.string.workspace_local_title_hint, channel.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameChannel(channelId, renameTitle.trim().takeIf(String::isNotEmpty))
                            renameChannelId = null
                        },
                        enabled = !busy,
                    ) { Text(stringResource(Res.string.workspace_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameChannelId = null }, enabled = !busy) {
                        Text(stringResource(Res.string.workspace_cancel))
                    }
                },
            )
        }
    }

    removeChannelId?.let { channelId ->
        val channel = state.channels.firstOrNull { it.id == channelId }
        if (channel == null) {
            removeChannelId = null
        } else {
            AlertDialog(
                onDismissRequest = { if (!busy) removeChannelId = null },
                title = { Text(stringResource(Res.string.workspace_remove_channel_title, channel.displayName)) },
                text = { Text(stringResource(Res.string.workspace_remove_channel_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemoveChannel(channelId)
                            removeChannelId = null
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            text = stringResource(Res.string.workspace_confirm_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removeChannelId = null }, enabled = !busy) {
                        Text(stringResource(Res.string.workspace_cancel))
                    }
                },
            )
        }
    }
}

@Composable
internal fun WorkspaceAddChannelDialog(
    busy: Boolean,
    canAddChannel: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var login by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.workspace_add_channel_title)) },
        text = {
            OutlinedTextField(
                value = login,
                onValueChange = { login = it.take(26) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.workspace_channel_login)) },
                singleLine = true,
                enabled = !busy && canAddChannel,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedLogin = login.trim()
                    if (normalizedLogin.isNotEmpty() && canAddChannel) {
                        onAdd(normalizedLogin)
                        onDismiss()
                    }
                },
                enabled = !busy && canAddChannel && login.isNotBlank(),
            ) {
                Text(stringResource(Res.string.workspace_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.workspace_cancel))
            }
        },
    )
}

private fun compactAttentionCount(value: Int): String = if (value > 99) "99+" else value.toString()

internal const val MAX_WORKSPACE_CHANNELS = 20
