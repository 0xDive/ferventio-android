package io.ferventio.shared.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.TwitchChatMessageScopeException
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_composer_placeholder
import io.ferventio.shared.generated.resources.chat_emotes
import io.ferventio.shared.generated.resources.chat_message_too_long
import io.ferventio.shared.generated.resources.chat_read_only_sign_in_to_send
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
    thirdPartyEmotes: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication

    if (authentication == null) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(Res.string.chat_read_only_sign_in_to_send),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val hasWriteScope = authentication.accessLease?.session?.scopes?.contains(WRITE_CHAT_SCOPE) == true
    var draft by rememberSaveable(channel.id) { mutableStateOf("") }
    var sending by remember(channel.id) { mutableStateOf(false) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var emotePickerVisible by rememberSaveable(channel.id) { mutableStateOf(false) }
    val trimmed = draft.trim()
    val tooLong = trimmed.length > MAX_CHAT_MESSAGE_LENGTH
    val canSend = hasWriteScope && trimmed.isNotEmpty() && !tooLong && !sending
    val scopeRequiredText = stringResource(Res.string.chat_write_scope_required)
    val sendFailedFormat = stringResource(Res.string.chat_send_failed, "%s")

    fun submit() {
        if (!canSend) return
        val outgoingText = trimmed
        val replyParentMessageId = replyTarget
            ?.serverMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: replyTarget?.id
        sending = true
        errorMessage = null
        scope.launch {
            try {
                runtime.chatMessages.send(
                    authentication = authentication,
                    channel = channel,
                    message = outgoingText,
                    replyParentMessageId = replyParentMessageId,
                )
                draft = ""
                emotePickerVisible = false
                onSent()
            } catch (_: TwitchChatMessageScopeException) {
                errorMessage = scopeRequiredText
            } catch (error: Throwable) {
                errorMessage = sendFailedFormat.replace(
                    "%s",
                    error.message.orEmpty().ifBlank { "Twitch error" },
                )
            } finally {
                sending = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        replyTarget?.let { target ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                Res.string.chat_replying_to,
                                target.userDisplayName.ifBlank { target.userLogin },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = target.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onCancelReply, enabled = !sending) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.chat_reply_cancel),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { value ->
                            draft = value.take(MAX_CHAT_MESSAGE_LENGTH + 1)
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !sending,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            Res.string.chat_composer_placeholder,
                                            channel.displayName,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(
                        onClick = { emotePickerVisible = !emotePickerVisible },
                        enabled = !sending && hasWriteScope,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.InsertEmoticon,
                            contentDescription = stringResource(Res.string.chat_emotes),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = { submit() },
                enabled = canSend,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.chat_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        when {
            !hasWriteScope -> Text(
                text = scopeRequiredText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            tooLong -> Text(
                text = stringResource(Res.string.chat_message_too_long, trimmed.length),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            sending -> Text(
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

        if (emotePickerVisible) {
            SharedEmotePickerPanel(
                emotes = thirdPartyEmotes.values.toList(),
                onSelect = { asset ->
                    draft = appendEmoteCode(draft, asset.code)
                    errorMessage = null
                },
                onDismiss = { emotePickerVisible = false },
            )
        }
    }
}

private fun appendEmoteCode(input: String, code: String): String = buildString {
    append(input.trimEnd())
    if (isNotEmpty()) append(' ')
    append(code)
    append(' ')
}
