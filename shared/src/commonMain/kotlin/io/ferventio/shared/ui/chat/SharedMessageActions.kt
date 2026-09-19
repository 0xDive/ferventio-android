package io.ferventio.shared.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_action_copy
import io.ferventio.shared.generated.resources.chat_action_delete
import io.ferventio.shared.generated.resources.chat_action_pin
import io.ferventio.shared.generated.resources.chat_action_reply
import io.ferventio.shared.generated.resources.chat_action_thread
import io.ferventio.shared.generated.resources.chat_action_unpin
import io.ferventio.shared.generated.resources.chat_action_user
import io.ferventio.shared.generated.resources.chat_actions_title
import io.ferventio.shared.generated.resources.chat_thread_empty
import io.ferventio.shared.generated.resources.chat_thread_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMessageActionsSheet(
    message: ChatMessage,
    canReply: Boolean,
    canOpenThread: Boolean,
    canOpenUser: Boolean,
    canDelete: Boolean,
    canPin: Boolean,
    canUnpin: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onOpenThread: () -> Unit,
    onCopy: () -> Unit,
    onOpenUser: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        ) {
            Text(
                text = stringResource(Res.string.chat_actions_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            MessageActionItem(
                icon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                label = stringResource(Res.string.chat_action_reply),
                enabled = canReply,
                onClick = onReply,
            )
            MessageActionItem(
                icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                label = stringResource(Res.string.chat_action_thread),
                enabled = canOpenThread,
                onClick = onOpenThread,
            )
            MessageActionItem(
                icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                label = stringResource(Res.string.chat_action_copy),
                enabled = message.text.isNotBlank(),
                onClick = onCopy,
            )
            MessageActionItem(
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = stringResource(Res.string.chat_action_user),
                enabled = canOpenUser,
                onClick = onOpenUser,
            )
            if (canPin || canUnpin) {
                MessageActionItem(
                    icon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    label = stringResource(
                        if (canUnpin) Res.string.chat_action_unpin else Res.string.chat_action_pin,
                    ),
                    enabled = true,
                    onClick = if (canUnpin) onUnpin else onPin,
                )
            }
            if (canDelete) {
                MessageActionItem(
                    icon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    label = stringResource(Res.string.chat_action_delete),
                    enabled = true,
                    onClick = onDelete,
                    labelColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MessageActionItem(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (enabled) labelColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = icon,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedReplyThreadSheet(
    target: ChatMessage,
    messages: List<ChatMessage>,
    canReply: Boolean,
    onDismiss: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onOpenUser: (ChatMessage) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        ) {
            Text(
                text = stringResource(Res.string.chat_thread_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (messages.isEmpty()) {
                Text(
                    text = stringResource(Res.string.chat_thread_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(
                        items = messages,
                        key = { message -> message.serverMessageId ?: message.id },
                    ) { message ->
                        SharedThreadMessageRow(
                            message = message,
                            canReply = canReply && !message.isSystem && !message.isDeleted,
                            onReply = { onReply(message) },
                            onOpenUser = { onOpenUser(message) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedThreadMessageRow(
    message: ChatMessage,
    canReply: Boolean,
    onReply: () -> Unit,
    onOpenUser: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.userDisplayName.ifBlank { message.userLogin },
                modifier = Modifier.weight(1f).clickable(onClick = onOpenUser),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canReply) {
                TextButton(onClick = onReply) {
                    Text(stringResource(Res.string.chat_action_reply))
                }
            }
        }
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun resolveSharedReplyThreadMessages(
    target: ChatMessage,
    messages: List<ChatMessage>,
): List<ChatMessage> {
    val targetCanonicalId = target.serverMessageId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: target.id
    val rootId = target.reply?.threadMessageId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: target.reply?.parentMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        ?: targetCanonicalId

    return messages.asSequence()
        .filter { message ->
            val canonicalId = message.serverMessageId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: message.id
            canonicalId == rootId ||
                message.reply?.threadMessageId == rootId ||
                message.reply?.parentMessageId == rootId
        }
        .distinctBy { message -> message.serverMessageId ?: message.id }
        .sortedWith(compareBy(ChatMessage::timestampMillis, ChatMessage::id))
        .toList()
}
