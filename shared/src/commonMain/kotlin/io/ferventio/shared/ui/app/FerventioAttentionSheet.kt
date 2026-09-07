package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.AttentionEntry
import io.ferventio.shared.chat.ChatAttentionStateHolder
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.attention_channel
import io.ferventio.shared.generated.resources.attention_direct_mention
import io.ferventio.shared.generated.resources.attention_empty
import io.ferventio.shared.generated.resources.attention_highlight
import io.ferventio.shared.generated.resources.attention_title
import io.ferventio.shared.generated.resources.attention_unread
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioAttentionSheet(
    attention: ChatAttentionStateHolder,
    onOpenEntry: (AttentionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember(attention.attentionEntries) {
        attention.attentionEntries.sortedWith(
            compareBy<AttentionEntry> { it.isRead }
                .thenByDescending(AttentionEntry::timestampMillis),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.attention_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(Res.string.attention_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                ) {
                    items(
                        items = entries,
                        key = AttentionEntry::messageId,
                    ) { entry ->
                        AttentionEntryRow(
                            entry = entry,
                            onClick = { onOpenEntry(entry) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionEntryRow(
    entry: AttentionEntry,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.attention_channel, entry.channelLogin),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = entry.authorDisplayName.ifBlank { entry.authorLogin },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.isRead) {
                    AttentionTag(
                        text = stringResource(Res.string.attention_unread),
                        emphasized = true,
                    )
                }
            }
            Text(
                text = entry.text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isDirectMention) {
                    AttentionTag(text = stringResource(Res.string.attention_direct_mention))
                }
                if (entry.isHighlight) {
                    AttentionTag(text = stringResource(Res.string.attention_highlight))
                }
            }
        }
    }
}

@Composable
private fun AttentionTag(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
