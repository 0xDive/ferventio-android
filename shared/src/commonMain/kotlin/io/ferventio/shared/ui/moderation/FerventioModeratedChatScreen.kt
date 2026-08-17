package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.nuke_preview_action
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.chat.FerventioChatTimeline
import org.jetbrains.compose.resources.stringResource

@Composable
fun FerventioModeratedChatScreen(
    channel: ChatChannel,
    moderatorChannelIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val canPreview = canPreviewNuke(channel.id, moderatorChannelIds)
    var showNukePreview by remember(channel.id) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (canPreview) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNukePreview = true }) {
                        Text(stringResource(Res.string.nuke_preview_action))
                    }
                }
            }
        }

        FerventioChatTimeline(
            channel = channel,
            modifier = Modifier.weight(1f),
        )
    }

    if (showNukePreview && canPreview) {
        NukePreviewSheet(
            messages = runtime.chat.messages(channel.id),
            onDismiss = { showNukePreview = false },
        )
    }
}
