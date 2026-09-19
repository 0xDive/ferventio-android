package io.ferventio.shared.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.PinnedChatMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_pinned_by
import io.ferventio.shared.generated.resources.chat_pinned_message
import io.ferventio.shared.generated.resources.chat_pinned_unpin
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedPinnedMessageBanner(
    pinned: PinnedChatMessage,
    canUnpin: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = stringResource(Res.string.chat_pinned_message),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = pinned.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    pinned.pinnedByUserName?.takeIf(String::isNotBlank)?.let { name ->
                        Text(
                            text = stringResource(Res.string.chat_pinned_by, name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                        )
                    }
                }
            }
            if (canUnpin) {
                TextButton(onClick = onUnpin) {
                    Text(stringResource(Res.string.chat_pinned_unpin))
                }
            }
        }
    }
}
