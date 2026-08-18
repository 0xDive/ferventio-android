package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.TwitchChatMessageScopeException
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_composer_placeholder
import io.ferventio.shared.generated.resources.chat_message_too_long
import io.ferventio.shared.generated.resources.chat_reply_cancel
import io.ferventio.shared.generated.resources.chat_replying_to
import io.ferventio.shared.generated.resources.chat_send
import io.ferventio.shared.generated.resources.chat_send_failed
import io.ferventio.shared.generated.resources.chat_sending
import io.ferventio.shared.generated.resources.chat_write_scope_required
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val MAX_CHAT_MESSAGE_LENGTH = 500
private const val WRITE_CHAT_SCOPE = "user:write:chat"

@Composable
fun SharedChatComposer(
    channel: ChatChannel,
    replyTarget: ChatMessage?,
    onCancelReply: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val scope = rememberCoroutineScope()
    val authentication = runtime.authentication.state.authentication
    val hasWriteScope = authentication?.accessLease?.session?.scopes?.contains(WRITE_CHAT_SCOPE) == true
    var draft by remember(channel.id) { mutableStateOf("") }
    var sending by remember(channel.id) { mutableStateOf(false) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    val trimmed = draft.trim()
    val tooLong = trimmed.length > MAX_CHAT_MESSAGE_LENGTH
    val canSend = authentication != null &&
        hasWriteScope &&
        trimmed.isNotEmpty() &&
        !tooLong &&
        !sending
    val scopeRequiredText = stringResource(Res.string.chat_write_scope_required)
    val sendFailedFormat = stringResource(Res.string.chat_send_failed, "%s")

    fun submit() {
        val auth = authentication ?: return
        if (!canSend) return
        val outgoingText = trimmed
        sending = true
        errorMessage = null
        scope.launch {
            try {
                runtime.chatMessages.send(
                    authentication = auth,
                    channel = channel,
                    message = outgoingText,
                    replyParentMessageId = replyTarget?.id,
                )
                draft = ""
                onSent()
            } catch (_: TwitchChatMessageScopeException) {
                errorMessage = scopeRequiredText
            } catch (error: Throwable) {
                errorMessage = sendFailedFormat.replace("%s", error.message.orEmpty().ifBlank { "Twitch error" })
            } finally {
                sending = false
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            replyTarget?.let { target ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    Res.string.chat_replying_to,
                                    target.userDisplayName.ifBlank { target.userLogin },
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = target.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = onCancelReply, enabled = !sending) {
                            Text(stringResource(Res.string.chat_reply_cancel))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                    placeholder = {
                        Text(stringResource(Res.string.chat_composer_placeholder, channel.displayName))
                    },
                    supportingText = when {
                        !hasWriteScope && authentication != null -> ({ Text(scopeRequiredText) })
                        tooLong -> ({ Text(stringResource(Res.string.chat_message_too_long, trimmed.length)) })
                        else -> null
                    },
                    isError = tooLong || (!hasWriteScope && authentication != null),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
                Button(
                    onClick = { submit() },
                    enabled = canSend,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(Res.string.chat_send))
                    }
                }
            }

            if (sending) {
                Text(
                    text = stringResource(Res.string.chat_sending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
