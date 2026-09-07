package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.quick_moderation_ban
import io.ferventio.shared.generated.resources.quick_moderation_ban_body
import io.ferventio.shared.generated.resources.quick_moderation_ban_title
import io.ferventio.shared.generated.resources.quick_moderation_cancel
import io.ferventio.shared.generated.resources.quick_moderation_delete
import io.ferventio.shared.generated.resources.quick_moderation_delete_body
import io.ferventio.shared.generated.resources.quick_moderation_delete_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuickModerationActionStrip(
    message: ChatMessage,
    canBan: Boolean,
    canDelete: Boolean,
    confirmActions: Boolean,
    onBan: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var pendingAction by remember(message.id) { mutableStateOf<QuickModerationAction?>(null) }
    val target = message.userLogin.trim().ifBlank { message.userDisplayName.trim() }
    val banBody = stringResource(Res.string.quick_moderation_ban_body)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatChatTimestamp(message.timestampMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canBan && onBan != null) {
            TextButton(
                onClick = {
                    if (confirmActions) pendingAction = QuickModerationAction.BAN else onBan()
                },
            ) {
                Text(
                    text = stringResource(Res.string.quick_moderation_ban),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (canDelete && onDelete != null) {
            TextButton(
                onClick = {
                    if (confirmActions) pendingAction = QuickModerationAction.DELETE else onDelete()
                },
            ) {
                Text(
                    text = stringResource(Res.string.quick_moderation_delete),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    when (pendingAction) {
        QuickModerationAction.BAN -> AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(Res.string.quick_moderation_ban_title)) },
            text = {
                Text(
                    if (target.isBlank()) banBody else "$banBody\n@$target",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        onBan?.invoke()
                    },
                ) {
                    Text(stringResource(Res.string.quick_moderation_ban))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(Res.string.quick_moderation_cancel))
                }
            },
        )

        QuickModerationAction.DELETE -> AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(Res.string.quick_moderation_delete_title)) },
            text = { Text(stringResource(Res.string.quick_moderation_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        onDelete?.invoke()
                    },
                ) {
                    Text(stringResource(Res.string.quick_moderation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(Res.string.quick_moderation_cancel))
                }
            },
        )

        null -> Unit
    }
}

private enum class QuickModerationAction {
    BAN,
    DELETE,
}
