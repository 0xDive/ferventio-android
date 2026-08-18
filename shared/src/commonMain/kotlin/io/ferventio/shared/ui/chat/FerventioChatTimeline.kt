package io.ferventio.shared.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.ChatRepeatCollapseConfig
import io.ferventio.app.domain.ChatRepeatCollapser
import io.ferventio.app.domain.ChatRepeatSummary
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_message_deleted
import io.ferventio.shared.generated.resources.chat_replying_to
import io.ferventio.shared.generated.resources.chat_retry
import io.ferventio.shared.generated.resources.chat_send_failed
import io.ferventio.shared.generated.resources.chat_sending
import io.ferventio.shared.generated.resources.chat_status_connecting
import io.ferventio.shared.generated.resources.chat_status_creating_subscriptions
import io.ferventio.shared.generated.resources.chat_status_disconnected
import io.ferventio.shared.generated.resources.chat_status_failed
import io.ferventio.shared.generated.resources.chat_status_reconnecting
import io.ferventio.shared.generated.resources.chat_status_waiting_welcome
import io.ferventio.shared.generated.resources.chat_waiting_for_messages
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.SharedAppPreferences
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
    onReplyRequest: ((ChatMessage) -> Unit)? = null,
    onRetryMessage: ((ChatMessage) -> Unit)? = null,
) {
    val runtime = LocalFerventioRuntimeState.current
    val chat = runtime.chat
    val attention = runtime.attention
    val preferences = runtime.settings.preferences
    val canonicalMessages = chat.messages(channel.id)
    val sourceMessages = remember(canonicalMessages, preferences.showSystemMessages) {
        if (preferences.showSystemMessages) {
            canonicalMessages
        } else {
            canonicalMessages.filterNot { message -> message.isSystem }
        }
    }
    val collapsePlan = remember(sourceMessages, preferences.repeatCollapseEnabled) {
        ChatRepeatCollapser.build(
            messages = sourceMessages,
            config = ChatRepeatCollapseConfig(enabled = preferences.repeatCollapseEnabled),
        )
    }
    val messages = remember(sourceMessages, collapsePlan.visibleMessageIds) {
        sourceMessages.filter { message -> message.id in collapsePlan.visibleMessageIds }
    }
    val thirdPartyEmotes = rememberThirdPartyEmoteCatalog(
        channelId = channel.id,
        betterTtvEnabled = preferences.betterTtvEnabled,
        frankerFaceZEnabled = preferences.frankerFaceZEnabled,
        sevenTvEnabled = preferences.sevenTvEnabled,
    )
    val cheermoteAssets = chat.cheermoteAssets(channel.id)
    val listState = rememberLazyListState()
    var followTail by remember(channel.id) { mutableStateOf(preferences.autoScrollEnabled) }

    DisposableEffect(channel.id, attention) {
        attention.updateViewport(
            channelId = channel.id,
            visible = true,
            isAtLiveTail = !listState.canScrollForward,
        )
        onDispose {
            attention.updateViewport(
                channelId = channel.id,
                visible = false,
                isAtLiveTail = false,
            )
        }
    }
    LaunchedEffect(channel.id, listState, attention) {
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { isAtLiveTail ->
                attention.updateViewport(
                    channelId = channel.id,
                    visible = true,
                    isAtLiveTail = isAtLiveTail,
                )
            }
    }
    LaunchedEffect(preferences.autoScrollEnabled) {
        followTail = preferences.autoScrollEnabled && !listState.canScrollForward
    }
    LaunchedEffect(listState, preferences.autoScrollEnabled) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                followTail = preferences.autoScrollEnabled && !listState.canScrollForward
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
                        preferences = preferences,
                        repeatSummary = collapsePlan.summaryFor(message.id),
                        thirdPartyEmotes = thirdPartyEmotes,
                        cheermoteAssets = cheermoteAssets,
                        onAuthorClick = onAuthorClick,
                        onReplyRequest = onReplyRequest,
                        onRetryMessage = onRetryMessage,
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
    preferences: SharedAppPreferences,
    repeatSummary: ChatRepeatSummary?,
    thirdPartyEmotes: Map<String, ThirdPartyEmoteAsset>,
    cheermoteAssets: Map<String, List<CheermoteAsset>>,
    onAuthorClick: ((ChatMessage) -> Unit)?,
    onReplyRequest: ((ChatMessage) -> Unit)?,
    onRetryMessage: ((ChatMessage) -> Unit)?,
) {
    val chat = LocalFerventioRuntimeState.current.chat
    val deletedPlaceholder = stringResource(Res.string.chat_message_deleted)
    val presentation = projectChatMessage(
        message = message,
        deletedPlaceholder = deletedPlaceholder,
        thirdPartyEmotes = thirdPartyEmotes,
        cheermoteAssetsByPrefix = cheermoteAssets,
        animatedMediaSupported = supportsAnimatedChatMedia && preferences.animateEmotes,
        showDeletedMessageContent = preferences.showDeletedMessageContent,
    )
    val renderSegments = groupChatMessageSegments(presentation.segments)
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val mentionColor = MaterialTheme.colorScheme.tertiary
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val authorLabel = message.authorLabel(preferences.nameStyle)
    val rowVerticalPadding = when (preferences.messageDensity) {
        MessageDensity.COMPACT -> 2.dp
        MessageDensity.NORMAL -> 5.dp
        MessageDensity.RELAXED -> 8.dp
    }
    val emoteScale = preferences.emoteScalePercent / 100f
    val badges = if (preferences.showBadges) presentation.badges else emptyList()
    val avatarImageUrl = message.author.profileImageUrl
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        .takeIf { preferences.showAvatars }
    val replyMessageId = message.serverMessageId?.trim()?.takeIf(String::isNotEmpty) ?: message.id
    val canReply = onReplyRequest != null &&
        !message.isSystem &&
        !message.isDeleted &&
        !replyMessageId.startsWith("local-")
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
            if (preferences.showTimestamps) {
                withStyle(SpanStyle(color = metadataColor)) {
                    append("[")
                    append(formatChatTimestamp(message.timestampMillis))
                    append("] ")
                }
            }
            if (avatarImageUrl != null) {
                appendInlineContent(inlineAvatarId(), authorLabel)
                append(" ")
            }
            if (message.isAction) append("* ")
            badges.forEachIndexed { index, _ ->
                appendInlineContent(inlineBadgeId(index), "◆")
                append(" ")
            }
            val authorStart = length
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(authorLabel)
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
            avatarImageUrl?.let { imageUrl ->
                put(
                    inlineAvatarId(),
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = 1.2.em,
                            height = 1.2.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        SharedAvatarIcon(
                            imageUrl = imageUrl,
                            label = authorLabel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
            badges.forEachIndexed { index, badge ->
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
                if (renderSegment.base.imageUrl == null) return@forEachIndexed
                if (!renderSegment.base.kind.isInlineEmote()) return@forEachIndexed
                val bttvVisualState = BttvEmoteVisualState(renderSegment.base.bttvModifiers)
                put(
                    inlineSegmentId(index),
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = (1.35f * emoteScale * bttvVisualState.widthMultiplier).em,
                            height = (1.35f * emoteScale).em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        SharedInlineEmoteStack(
                            base = renderSegment.base,
                            overlays = renderSegment.overlays,
                            animationsEnabled = preferences.animateEmotes,
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
                .padding(horizontal = 12.dp, vertical = rowVerticalPadding)
                .pointerInput(text, onAuthorClick, onReplyRequest, canReply) {
                    detectTapGestures(
                        onLongPress = {
                            if (canReply) onReplyRequest?.invoke(message)
                        },
                        onTap = { position ->
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
                        },
                    )
                },
            style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor),
            inlineContent = inlineContent,
            maxLines = if (preferences.wrapMessageLines) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult = it },
        )

        when (message.outgoingState) {
            OutgoingMessageState.SENDING -> Text(
                text = stringResource(Res.string.chat_sending),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = metadataColor,
            )
            OutgoingMessageState.FAILED -> Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.chat_send_failed,
                        message.outgoingError.orEmpty().ifBlank { "Twitch error" },
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onRetryMessage != null) {
                    TextButton(onClick = { onRetryMessage(message) }) {
                        Text(stringResource(Res.string.chat_retry))
                    }
                }
            }
            OutgoingMessageState.NONE,
            OutgoingMessageState.SENT -> Unit
        }

        repeatSummary?.let { summary ->
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = rowVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatCountBadge(summary = summary)
                formatRepeatParticipantSummary(summary)
                    .takeIf(String::isNotBlank)
                    ?.let { participants ->
                        Text(
                            text = participants,
                            style = MaterialTheme.typography.labelSmall,
                            color = metadataColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }
    }
}

private fun ChatMessage.authorLabel(style: ChatNameStyle): String {
    val displayName = author.displayName.trim().ifBlank { author.login.trim() }
    val login = author.login.trim().ifBlank { displayName }
    return when (style) {
        ChatNameStyle.DISPLAY_NAME -> displayName
        ChatNameStyle.LOGIN -> login
        ChatNameStyle.DISPLAY_AND_LOGIN -> if (displayName.equals(login, ignoreCase = true)) {
            displayName
        } else {
            "$displayName ($login)"
        }
    }
}

private fun ChatMessageSegmentKind.isInlineEmote(): Boolean = when (this) {
    ChatMessageSegmentKind.TWITCH_EMOTE,
    ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
    ChatMessageSegmentKind.GIF,
    ChatMessageSegmentKind.CHEERMOTE -> true
    else -> false
}

private fun inlineAvatarId(): String = "avatar"
private fun inlineBadgeId(index: Int): String = "badge_$index"
private fun inlineSegmentId(index: Int): String = "segment_$index"

@Composable
private fun SharedInlineEmoteStack(
    base: ChatMessageSegment,
    overlays: List<ChatMessageSegment>,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseVisualState = BttvEmoteVisualState(base.bttvModifiers)
    val baseAnimatedEffects = if (animationsEnabled) {
        rememberBttvAnimatedEffects(baseVisualState)
    } else {
        BttvAnimatedEffects(colorFilter = bttvCursedColorFilter(baseVisualState))
    }
    Box(
        modifier = modifier
            .applyBttvStaticEffects(baseVisualState)
            .offset(x = baseAnimatedEffects.shakeOffsetDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        SharedInlineEmote(
            imageUrl = requireNotNull(base.imageUrl),
            code = base.text,
            animatedEffects = baseAnimatedEffects,
            modifier = Modifier.fillMaxSize(),
            showFallback = true,
        )
        overlays.forEach { overlay ->
            val imageUrl = overlay.imageUrl ?: return@forEach
            val overlayVisualState = BttvEmoteVisualState(overlay.bttvModifiers)
            val overlayAnimatedEffects = if (animationsEnabled) {
                rememberBttvAnimatedEffects(overlayVisualState)
            } else {
                BttvAnimatedEffects(colorFilter = bttvCursedColorFilter(overlayVisualState))
            }
            SharedInlineEmote(
                imageUrl = imageUrl,
                code = overlay.text,
                animatedEffects = overlayAnimatedEffects,
                modifier = Modifier
                    .fillMaxSize()
                    .applyBttvStaticEffects(overlayVisualState)
                    .offset(x = overlayAnimatedEffects.shakeOffsetDp.dp),
                showFallback = false,
            )
        }
    }
}

@Composable
private fun SharedInlineEmote(
    imageUrl: String,
    code: String,
    animatedEffects: BttvAnimatedEffects = BttvAnimatedEffects(),
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
                colorFilter = animatedEffects.colorFilter,
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
private fun SharedAvatarIcon(
    imageUrl: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl)
    val state by painter.state.collectAsState()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (state is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
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
