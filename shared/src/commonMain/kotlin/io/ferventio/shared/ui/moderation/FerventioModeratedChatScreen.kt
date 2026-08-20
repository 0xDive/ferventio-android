package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import io.ferventio.shared.generated.resources.quick_moderation_auth_required
import io.ferventio.shared.generated.resources.quick_moderation_error_title
import io.ferventio.shared.generated.resources.quick_moderation_failed
import io.ferventio.shared.generated.resources.quick_moderation_ok
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
    val authenticationRequiredText = stringResource(Res.string.quick_moderation_auth_required)
    var showNukePreview by remember(channel.id) { mutableStateOf(false) }
    var selectedUserMessage by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var replyTarget by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var quickModerationError by remember(channel.id) { mutableStateOf<String?>(null) }

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
            canModerate = canModerateChannel,
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
            onQuickBan = { message ->
                val authentication = runtime.authentication.state.authentication
                if (authentication == null) {
                    quickModerationError = authenticationRequiredText
                } else {
                    val availability = quickModerationAvailability(
                        message = message,
                        ownUserId = authentication.accessLease?.session?.userId,
                        canModerate = canModerateChannel,
                        preferences = runtime.localUiPreferences.preferences,
                    )
                    if (availability.canBan) {
                        scope.launch {
                            try {
                                runtime.moderation.banUser(
                                    authentication = authentication,
                                    broadcasterId = channel.id,
                                    targetUserId = message.userId,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                quickModerationError = error.message.orEmpty().ifBlank { "Twitch error" }
                            }
                        }
                    }
                }
            },
            onQuickDelete = { message ->
                val authentication = runtime.authentication.state.authentication
                if (authentication == null) {
                    quickModerationError = authenticationRequiredText
                } else {
                    val availability = quickModerationAvailability(
                        message = message,
                        ownUserId = authentication.accessLease?.session?.userId,
                        canModerate = canModerateChannel,
                        preferences = runtime.localUiPreferences.preferences,
                    )
                    if (availability.canDelete) {
                        val messageId = message.serverMessageId
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: message.id
                        scope.launch {
                            try {
                                runtime.moderation.deleteChatMessage(
                                    authentication = authentication,
                                    broadcasterId = channel.id,
                                    messageId = messageId,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                quickModerationError = error.message.orEmpty().ifBlank { "Twitch error" }
                            }
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
            messages = nukePreviewMessages(runtime.chat, channel.id),
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

    quickModerationError?.let { error ->
        AlertDialog(
            onDismissRequest = { quickModerationError = null },
            title = { Text(stringResource(Res.string.quick_moderation_error_title)) },
            text = { Text(stringResource(Res.string.quick_moderation_failed, error)) },
            confirmButton = {
                TextButton(onClick = { quickModerationError = null }) {
                    Text(stringResource(Res.string.quick_moderation_ok))
                }
            },
        )
    }
}
