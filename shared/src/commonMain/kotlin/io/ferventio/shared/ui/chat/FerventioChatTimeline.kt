package io.ferventio.shared.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.ThirdPartyEmoteAsset
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
private const val AUTHOR_ANNOTATION_TAG = "author"

@Composable
fun FerventioChatTimeline(
    channel: ChatChannel,
    modifier: Modifier = Modifier,
    onAuthorClick: ((ChatMessage) -> Unit)? = null,
) {
    val chat = LocalFerventioRuntimeState.current.chat
    val messages = chat.messages(channel.id)
    val thirdPartyEmotes = rememberThirdPartyEmoteCatalog(channel.id)
    val cheermoteAssets = chat.cheermoteAssets(channel.id)
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
                    ChatMessageRow(
                        message = message,
                        thirdPartyEmotes = thirdPartyEmotes,
                        cheermoteAssets = cheermoteAssets,
                        onAuthorClick = onAuthorClick,
                    )
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
private fun ChatMessageRow(
    message: ChatMessage,
    thirdPartyEmotes: Map<String, ThirdPartyEmoteAsset>,
    cheermoteAssets: Map<String, List<CheermoteAsset>>,
    onAuthorClick: ((ChatMessage) -> Unit)?,
) {
    val chat = LocalFerventioRuntimeState.current.chat
    val deletedPlaceholder = stringResource(Res.string.chat_message_deleted)
    val presentation = projectChatMessage(
        message = message,
        deletedPlaceholder = deletedPlaceholder,
        thirdPartyEmotes = thirdPartyEmotes,
        cheermoteAssetsByPrefix = cheermoteAssets,
    )
    val renderSegments = groupChatMessageSegments(presentation.segments)
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val mentionColor = MaterialTheme.colorScheme.tertiary
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface
    var textLayoutResult by remember(message.id) { mutableStateOf<TextLayoutResult?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        presentation.reply?.let { reply ->
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.chat_replying_to, reply.authorLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = metadataColor,
                )
                reply.bodyPreview?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = metadataColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val text = buildAnnotatedString {
            if (message.isAction) append("* ")
            presentation.badges.forEachIndexed { index, _ ->
                appendInlineContent(inlineBadgeId(index), "◆")
                append(" ")
            }
            val authorStart = length
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(message.author.displayName.ifBlank { message.author.login })
            }
            val authorEnd = length
            if (authorEnd > authorStart) {
                addStringAnnotation(
                    tag = AUTHOR_ANNOTATION_TAG,
                    annotation = message.id,
                    start = authorStart,
                    end = authorEnd,
                )
            }
            append(if (message.isAction) " " else ": ")

            withStyle(
                SpanStyle(
                    fontStyle = if (message.isAction || presentation.isDeleted) FontStyle.Italic
                    else FontStyle.Normal,
                ),
            ) {
                renderSegments.forEachIndexed { index, renderSegment ->
                    val segment = renderSegment.base
                    if (segment.imageUrl != null && segment.kind.isInlineEmote()) {
                        appendInlineContent(inlineSegmentId(index), segment.text.ifBlank { "emote" })
                        return@forEachIndexed
                    }
                    val start = length
                    append(segment.text)
                    val end = length
                    if (end <= start) return@forEachIndexed
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
                                addStringAnnotation(URL_ANNOTATION_TAG, url, start, end)
                            }
                        }
                        ChatMessageSegmentKind.MENTION -> addStyle(
                            SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold),
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

        val inlineContent = buildMap<String, InlineTextContent> {
            presentation.badges.forEachIndexed { index, badge ->
                put(
                    inlineBadgeId(index),
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = 1.05.em,
                            height = 1.05.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        SharedBadgeIcon(
                            badge = badge,
                            asset = chat.badgeAsset(message.channelId, badge),
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
            renderSegments.forEachIndexed { index, renderSegment ->
                val imageUrl = renderSegment.base.imageUrl ?: return@forEachIndexed
                if (!renderSegment.base.kind.isInlineEmote()) return@forEachIndexed
                put(
                    inlineSegmentId(index),
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = 1.35.em,
                            height = 1.35.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        SharedInlineEmoteStack(
                            base = renderSegment.base,
                            overlays = renderSegment.overlays,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }

        BasicText(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .pointerInput(text, onAuthorClick) {
                    detectTapGestures { position ->
                        val offset = textLayoutResult?.getOffsetForPosition(position)
                            ?: return@detectTapGestures
                        val link = text.getStringAnnotations(URL_ANNOTATION_TAG, offset, offset).firstOrNull()
                        if (link != null) {
                            runCatching { uriHandler.openUri(link.item) }
                            return@detectTapGestures
                        }
                        if (onAuthorClick != null) {
                            val author = text.getStringAnnotations(AUTHOR_ANNOTATION_TAG, offset, offset)
                                .firstOrNull()
                            if (author != null) onAuthorClick(message)
                        }
                    }
                },
            style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor),
            inlineContent = inlineContent,
            onTextLayout = { textLayoutResult = it },
        )
    }
}

private fun ChatMessageSegmentKind.isInlineEmote(): Boolean = when (this) {
    ChatMessageSegmentKind.TWITCH_EMOTE,
    ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
    ChatMessageSegmentKind.GIF,
    ChatMessageSegmentKind.CHEERMOTE -> true
    else -> false
}

private fun inlineBadgeId(index: Int): String = "badge_$index"
private fun inlineSegmentId(index: Int): String = "segment_$index"

@Composable
private fun SharedInlineEmoteStack(
    base: ChatMessageSegment,
    overlays: List<ChatMessageSegment>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        SharedInlineEmote(
            imageUrl = requireNotNull(base.imageUrl),
            code = base.text,
            modifier = Modifier.fillMaxSize(),
            showFallback = true,
        )
        overlays.forEach { overlay ->
            val imageUrl = overlay.imageUrl ?: return@forEach
            SharedInlineEmote(
                imageUrl = imageUrl,
                code = overlay.text,
                modifier = Modifier.fillMaxSize(),
                showFallback = false,
            )
        }
    }
}

@Composable
private fun SharedInlineEmote(
    imageUrl: String,
    code: String,
    modifier: Modifier = Modifier,
    showFallback: Boolean = true,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl)
    val state by painter.state.collectAsState()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (state is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = code,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showFallback) {
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SharedBadgeIcon(
    badge: ChatBadge,
    asset: ChatBadgeAsset?,
    modifier: Modifier = Modifier,
) {
    val imageUrl = asset?.imageUrl2x?.takeIf(String::isNotBlank)
        ?: asset?.imageUrl1x?.takeIf(String::isNotBlank)
        ?: asset?.imageUrl4x?.takeIf(String::isNotBlank)
    val painter = rememberAsyncImagePainter(model = imageUrl)
    val painterState by painter.state.collectAsState()
    val symbol = when (badge.setId) {
        "broadcaster" -> "♛"
        "moderator", "vip" -> "◆"
        "subscriber" -> "★"
        "founder" -> "F"
        "partner" -> "✓"
        "staff" -> "S"
        "admin" -> "A"
        "global_mod" -> "G"
        "premium" -> "P"
        "turbo" -> "⚡"
        "bits" -> "B"
        else -> badge.setId.firstOrNull()?.uppercase() ?: "•"
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageUrl != null && painterState is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = asset?.title?.takeIf(String::isNotBlank) ?: badge.setId,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
