package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.nuke_preview_action
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.chat.FerventioChatTimeline
import io.ferventio.shared.ui.chat.InteractiveChatOverlayCards
import io.ferventio.shared.ui.chat.SharedChatComposer
import io.ferventio.shared.ui.user.SharedUserCardSheet
import io.ferventio.shared.ui.user.projectLocalUserCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun FerventioModeratedChatScreen(
    channel: ChatChannel,
    moderatorChannelIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val scope = rememberCoroutineScope()
    val canModerateChannel = canPreviewNuke(channel.id, moderatorChannelIds)
    var showNukePreview by remember(channel.id) { mutableStateOf(false) }
    var selectedUserMessage by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var replyTarget by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        if (canModerateChannel) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNukePreview = true }) {
                        Text(stringResource(Res.string.nuke_preview_action))
                    }
                }
            }
        }

        InteractiveChatOverlayCards(channelId = channel.id)

        FerventioChatTimeline(
            channel = channel,
            modifier = Modifier.weight(1f),
            onAuthorClick = { message -> selectedUserMessage = message },
            onReplyRequest = { message -> replyTarget = message },
            onRetryMessage = { message ->
                runtime.authentication.state.authentication?.let { authentication ->
                    scope.launch {
                        try {
                            runtime.chatMessages.retry(
                                authentication = authentication,
                                channel = channel,
                                failedMessage = message,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // The optimistic row owns and displays the retry error state.
                        }
                    }
                }
            },
        )

        SharedChatComposer(
            channel = channel,
            replyTarget = replyTarget,
            onCancelReply = { replyTarget = null },
            onSent = { replyTarget = null },
        )
    }

    if (showNukePreview && canModerateChannel) {
        NukePreviewSheet(
            channelId = channel.id,
            messages = runtime.chat.messages(channel.id),
            onDismiss = { showNukePreview = false },
        )
    }

    selectedUserMessage?.let { sourceMessage ->
        val messages = runtime.chat.messages(channel.id)
        val data = remember(sourceMessage.id, messages, canModerateChannel) {
            projectLocalUserCard(
                sourceMessage = sourceMessage,
                channelMessages = messages,
                canModerate = canModerateChannel,
            )
        }
        SharedUserCardSheet(
            data = data,
            onDismiss = { selectedUserMessage = null },
        )
    }
}
