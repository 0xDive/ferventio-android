package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_message_deleted
import io.ferventio.shared.generated.resources.chat_replying_to
import io.ferventio.shared.generated.resources.chat_status_connecting
import io.ferventio.shared.generated.resources.chat_status_creating_subscriptions
import io.ferventio.shared.generated.resources.chat_status_disconnected
import io.ferventio.shared.generated.resources.chat_status_failed
import io.ferventio.shared.generated.resources.chat_status_reconnecting
import io.ferventio.shared.generated.resources.chat_status_waiting_welcome
import io.ferventio.shared.generated.resources.chat_waiting_for_messages
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

private const val URL_ANNOTATION_TAG = "url"

@Composable
fun FerventioChatTimeline(
    channel: ChatChannel,
    modifier: Modifier = Modifier,
) {
    val chat = LocalFerventioRuntimeState.current.chat
    val messages = chat.messages(channel.id)
    val listState = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                followTail = !listState.canScrollForward
            }
    }
    LaunchedEffect(messages.size, followTail) {
        if (followTail && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatConnectionBanner(chat)
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.chat_waiting_for_messages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = messages,
                    key = ChatMessage::id,
                ) { message ->
                    ChatMessageRow(message)
                }
            }
        }
    }
}

@Composable
private fun ChatConnectionBanner(chat: ChatRuntimeStateHolder) {
    if (chat.connectionStatus == ConnectionStatus.CONNECTED) return

    val label = when (chat.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> stringResource(Res.string.chat_status_disconnected)
        ConnectionStatus.CONNECTING -> stringResource(Res.string.chat_status_connecting)
        ConnectionStatus.WAITING_WELCOME -> stringResource(Res.string.chat_status_waiting_welcome)
        ConnectionStatus.CREATING_SUBSCRIPTIONS ->
            stringResource(Res.string.chat_status_creating_subscriptions)
        ConnectionStatus.RECONNECTING -> stringResource(Res.string.chat_status_reconnecting)
        ConnectionStatus.FAILED -> stringResource(Res.string.chat_status_failed)
        ConnectionStatus.CONNECTED -> return
    }
    val detail = chat.connectionErrorMessage
        ?.takeIf(String::isNotBlank)
        ?.let { "$label: $it" }
        ?: label

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (chat.connectionStatus == ConnectionStatus.FAILED) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = detail,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (chat.connectionStatus == ConnectionStatus.FAILED) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val deletedPlaceholder = stringResource(Res.string.chat_message_deleted)
    val presentation = projectChatMessage(message, deletedPlaceholder)
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val mentionColor = MaterialTheme.colorScheme.tertiary
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        presentation.reply
            ?.authorLabel
            ?.takeIf(String::isNotBlank)
            ?.let { authorLabel ->
                Text(
                    text = stringResource(Res.string.chat_replying_to, authorLabel),
                    modifier = Modifier.padding(horizontal = 12.dp, top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = metadataColor,
                )
            }

        val text = buildAnnotatedString {
            if (message.isAction) {
                append("* ")
            }
            if (presentation.badgeLabels.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        color = metadataColor,
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append(presentation.badgeLabels.joinToString(" ") { badge -> "[$badge]" })
                    append(" ")
                }
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(message.author.displayName.ifBlank { message.author.login })
            }
            append(if (message.isAction) " " else ": ")

            withStyle(
                SpanStyle(
                    fontStyle = if (message.isAction || presentation.isDeleted) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                ),
            ) {
                presentation.segments.forEach { segment ->
                    val start = length
                    append(segment.text)
                    val end = length
                    if (end <= start) return@forEach

                    when (segment.kind) {
                        ChatMessageSegmentKind.LINK -> {
                            addStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                                start,
                                end,
                            )
                            segment.url?.let { url ->
                                addStringAnnotation(
                                    tag = URL_ANNOTATION_TAG,
                                    annotation = url,
                                    start = start,
                                    end = end,
                                )
                            }
                        }
                        ChatMessageSegmentKind.MENTION -> addStyle(
                            SpanStyle(
                                color = mentionColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            start,
                            end,
                        )
                        ChatMessageSegmentKind.TWITCH_EMOTE,
                        ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
                        ChatMessageSegmentKind.GIF -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Medium),
                            start,
                            end,
                        )
                        ChatMessageSegmentKind.CHEERMOTE -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            end,
                        )
                        ChatMessageSegmentKind.TEXT,
                        ChatMessageSegmentKind.UNKNOWN -> Unit
                    }
                }
            }
        }

        ClickableText(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor),
            onClick = { offset ->
                text.getStringAnnotations(
                    tag = URL_ANNOTATION_TAG,
                    start = offset,
                    end = offset,
                ).firstOrNull()?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
            },
        )
    }
}
