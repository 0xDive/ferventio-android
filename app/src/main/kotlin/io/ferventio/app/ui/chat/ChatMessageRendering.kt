package io.ferventio.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ferventio.app.BuildConfig
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatAssetResolver
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ComposerAutocomplete
import io.ferventio.app.domain.ComposerEmoteVisuals
import io.ferventio.app.domain.ComposerSuggestion
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageTextPreparation
import io.ferventio.app.domain.ChatLinkParser
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.resolveSplitFilterExpression
import io.ferventio.app.domain.ChatRateLimitState
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.CheermoteResolver
import io.ferventio.app.domain.EmoteCatalogRanking
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ChatNoticeTextFormatter
import io.ferventio.app.domain.ChatPresentationPolicy
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.SettingsSyncStatus
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.ReplyThreadResolver
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.ScrollRestorationPolicy
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.domain.resolve
import io.ferventio.app.domain.usageKey
import io.ferventio.app.domain.ThirdPartyEmoteCatalogResolver
import io.ferventio.app.domain.UserCardUiState
import io.ferventio.app.push.PushCoordinator
import io.ferventio.app.push.PushStatus
import io.ferventio.app.push.PushUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.core.graphics.toColorInt

@Composable
internal fun SystemMessageRow(
    message: ChatMessage,
    showTimestamp: Boolean,
    messageDensity: MessageDensity,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = when (messageDensity) {
                    MessageDensity.COMPACT -> 2.dp
                    MessageDensity.NORMAL -> 4.dp
                    MessageDensity.RELAXED -> 6.dp
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (showTimestamp) {
            Text(
                text = formatChatTimestamp(message.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                maxLines = 1,
            )
        }
        Text(
            text = "• ${message.text}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            fontStyle = FontStyle.Italic,
        )
    }
}

internal fun buildChatLine(
    message: ChatMessage,
    shownText: String,
    showTimestamp: Boolean,
    showAvatar: Boolean,
    showBadges: Boolean,
    frankerFaceZBadgeCount: Int,
    useFragments: Boolean,
    renderedGroupsByBaseIndex: Map<Int, RenderedEmoteGroup>,
    skippedFragmentIndices: Set<Int>,
    userColor: Color,
    timestampColor: Color,
    textColor: Color,
    accentColor: Color,
    nameStyle: ChatNameStyle,
    mentionColor: Color,
): ChatLineLayout {
    val identityRanges = mutableListOf<IntRange>()
    val emoteRanges = mutableListOf<EmoteTextRange>()
    val linkRanges = mutableListOf<ChatLinkTextRange>()
    val bodyFontStyle = if (message.isAction || message.isDeleted) FontStyle.Italic else FontStyle.Normal
    val safeTextColor = textColor.sanitizedForText(Color.White)
    val safeTimestampColor = timestampColor.sanitizedForText(safeTextColor)
    val safeUserColor = userColor.sanitizedForText(safeTextColor)
    val safeAccentColor = accentColor.sanitizedForText(safeTextColor)
    val safeMentionColor = mentionColor.sanitizedForText(safeAccentColor)
    val safeCheermoteColor = colorFromArgb(0xFFFFA000L)
    val text = buildAnnotatedString {
        if (showTimestamp) {
            withStyle(SpanStyle(color = safeTimestampColor)) {
                append(formatChatTimestamp(message.timestampMillis))
                append(" ")
            }
        }

        if (showAvatar) {
            val start = length
            appendInlineContent(CHAT_INLINE_AVATAR_ID, "◉")
            identityRanges += start until length
            append(" ")
        }

        if (showBadges) {
            message.badges.forEachIndexed { index, _ ->
                val start = length
                appendInlineContent(inlineBadgeId(index), "◆")
                identityRanges += start until length
                append(" ")
            }
            repeat(frankerFaceZBadgeCount) { index ->
                val start = length
                appendInlineContent(inlineFfzBadgeId(index), "◆")
                identityRanges += start until length
                append(" ")
            }
        }

        val nicknameStart = length
        withStyle(
            SpanStyle(
                color = safeUserColor,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(
                when (nameStyle) {
                    ChatNameStyle.DISPLAY_NAME -> message.userDisplayName
                    ChatNameStyle.LOGIN -> message.userLogin
                    ChatNameStyle.DISPLAY_AND_LOGIN -> if (message.userDisplayName.equals(message.userLogin, ignoreCase = true)) {
                        message.userDisplayName
                    } else {
                        "${message.userDisplayName} (${message.userLogin})"
                    }
                },
            )
        }
        identityRanges += nicknameStart until length

        withStyle(
            SpanStyle(
                color = safeTextColor,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(": ")
        }

        if (!useFragments) {
            appendChatTextWithLinks(
                value = shownText,
                textStyle = SpanStyle(color = safeTextColor, fontStyle = bodyFontStyle),
                linkStyle = SpanStyle(
                    color = safeAccentColor,
                    fontStyle = bodyFontStyle,
                    textDecoration = TextDecoration.Underline,
                ),
                linkRanges = linkRanges,
            )
        } else {
            message.fragments.forEachIndexed { index, fragment ->
                if (index in skippedFragmentIndices) return@forEachIndexed
                val group = renderedGroupsByBaseIndex[index]
                if (group != null) {
                    val start = length
                    appendInlineContent(inlineFragmentId(index), fragment.text.ifBlank { "emote" })
                    if (fragment is ChatFragment.Cheermote) {
                        withStyle(
                            SpanStyle(
                                color = group.info.accentColor
                                    ?.sanitizedForText(safeCheermoteColor)
                                    ?: safeCheermoteColor,
                                fontStyle = bodyFontStyle,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append(fragment.bits.toString())
                        }
                    }
                    emoteRanges += EmoteTextRange(start until length, group.info)
                    return@forEachIndexed
                }
                val textStyle = SpanStyle(color = safeTextColor, fontStyle = bodyFontStyle)
                val linkStyle = SpanStyle(
                    color = safeAccentColor,
                    fontStyle = bodyFontStyle,
                    textDecoration = TextDecoration.Underline,
                )
                when (fragment) {
                    is ChatFragment.Text -> appendChatTextWithLinks(
                        value = fragment.text,
                        textStyle = textStyle,
                        linkStyle = linkStyle,
                        linkRanges = linkRanges,
                    )
                    is ChatFragment.Link -> appendChatTextWithLinks(
                        value = fragment.text,
                        textStyle = textStyle,
                        linkStyle = linkStyle,
                        linkRanges = linkRanges,
                        fallbackUrl = fragment.url,
                    )
                    is ChatFragment.Mention -> withStyle(
                        SpanStyle(
                            color = safeMentionColor,
                            fontStyle = bodyFontStyle,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) { append(fragment.text) }
                    is ChatFragment.Cheermote -> withStyle(
                        SpanStyle(
                            color = safeCheermoteColor,
                            fontStyle = bodyFontStyle,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) { append(fragment.text) }
                    else -> withStyle(textStyle) { append(fragment.text) }
                }
            }
        }
    }
    return ChatLineLayout(
        text = text,
        identityRanges = identityRanges,
        emoteRanges = emoteRanges,
        linkRanges = linkRanges,
    )
}

internal fun AnnotatedString.Builder.appendChatTextWithLinks(
    value: String,
    textStyle: SpanStyle,
    linkStyle: SpanStyle,
    linkRanges: MutableList<ChatLinkTextRange>,
    fallbackUrl: String? = null,
) {
    val matches = ChatLinkParser.findAll(value)
    if (matches.isEmpty()) {
        val normalizedFallback = fallbackUrl?.let(ChatLinkParser::normalize)
        if (normalizedFallback != null && value.isNotBlank()) {
            val start = length
            withStyle(linkStyle) { append(value) }
            linkRanges += ChatLinkTextRange(start until length, normalizedFallback)
        } else {
            withStyle(textStyle) { append(value) }
        }
        return
    }
    var cursor = 0
    matches.forEach { match ->
        if (match.start > cursor) {
            withStyle(textStyle) { append(value.substring(cursor, match.start)) }
        }
        val start = length
        withStyle(linkStyle) { append(value.substring(match.start, match.endExclusive)) }
        linkRanges += ChatLinkTextRange(start until length, match.url)
        cursor = match.endExclusive
    }
    if (cursor < value.length) {
        withStyle(textStyle) { append(value.substring(cursor)) }
    }
}

internal fun inlineBadgeId(index: Int): String = "badge_$index"
internal fun inlineFfzBadgeId(index: Int): String = "ffz_badge_$index"
internal fun inlineFragmentId(index: Int): String = "fragment_$index"

@Composable
internal fun LayeredEmoteImage(
    urls: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        urls.forEachIndexed { index, url ->
            AsyncImage(
                model = url,
                contentDescription = if (index == urls.lastIndex) contentDescription else null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ChatBadgeAssetIcon(
    asset: ChatBadgeAsset,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = asset.imageUrl2x.ifBlank { asset.imageUrl1x },
        contentDescription = asset.title,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
internal fun ChatBadgeIcon(
    badge: ChatBadge,
    asset: ChatBadgeAsset?,
    modifier: Modifier = Modifier,
) {
    val imageUrl = asset?.imageUrl2x?.takeIf(String::isNotBlank)
        ?: asset?.imageUrl1x?.takeIf(String::isNotBlank)
    if (imageUrl == null) {
        FallbackBadgeIcon(badge = badge, modifier = modifier)
        return
    }

    val painter = rememberAsyncImagePainter(model = imageUrl)
    val painterState by painter.state.collectAsState()
    if (painterState is AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = asset?.title?.takeIf(String::isNotBlank) ?: badge.setId,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        FallbackBadgeIcon(badge = badge, modifier = modifier)
    }
}

@Composable
internal fun FallbackBadgeIcon(
    badge: ChatBadge,
    modifier: Modifier = Modifier,
) {
    val appearance = remember(badge.setId, badge.id) { badgeAppearance(badge.setId, badge.id) }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appearance.symbol,
            maxLines = 1,
            color = appearance.background,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal data class BadgeAppearance(
    val symbol: String,
    val background: Color,
    val foreground: Color = Color.White,
)

internal fun badgeAppearance(setId: String, badgeId: String): BadgeAppearance = when (setId) {
    "broadcaster" -> BadgeAppearance("♛", Color(0xFFE91916))
    "moderator" -> BadgeAppearance("◆", Color(0xFF00A86B))
    "vip" -> BadgeAppearance("◆", Color(0xFFE005B9))
    "subscriber" -> BadgeAppearance(
        symbol = if (badgeId == "0") "★" else badgeId.take(2),
        background = Color(0xFF7B2CBF),
    )
    "founder" -> BadgeAppearance("F", Color(0xFFC58B00))
    "partner" -> BadgeAppearance("✓", Color(0xFF9147FF))
    "staff" -> BadgeAppearance("S", Color(0xFF5B6472))
    "admin" -> BadgeAppearance("A", Color(0xFFE91916))
    "global_mod" -> BadgeAppearance("G", Color(0xFF00A86B))
    "premium" -> BadgeAppearance("P", Color(0xFF009CDC))
    "turbo" -> BadgeAppearance("⚡", Color(0xFF6441A5))
    "bits" -> BadgeAppearance("B", Color(0xFFFF8A00))
    else -> BadgeAppearance(
        symbol = setId.firstOrNull()?.uppercase() ?: "•",
        background = Color(0xFF59636F),
    )
}

@Composable
internal fun UserAvatar(
    imageUrl: String?,
    displayName: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        color = color.copy(alpha = 0.20f),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Аватар $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
    }
}

internal val EMPTY_MESSAGE_DECORATIONS: Map<String, MessageDecoration> = emptyMap()

internal val NOTICE_MESSAGE_TYPES = setOf(
    ChatMessageType.SUBSCRIPTION,
    ChatMessageType.RESUBSCRIPTION,
    ChatMessageType.GIFT_SUBSCRIPTION,
    ChatMessageType.RAID,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoticeMessageRow(
    message: ChatMessage,
    showTimestamp: Boolean,
    onOpenUser: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val notice = requireNotNull(message.notice)
    val accent = noticeAccentColor(message)
    val canOpenUser = message.userId.isNotBlank() &&
        !message.userId.startsWith("anonymous:") &&
        !message.userId.startsWith("twitch:")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = { if (canOpenUser) onOpenUser() },
                onLongClick = onOpenActions,
            ),
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.13f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (showTimestamp) {
                Text(
                    text = formatChatTimestamp(message.timestampMillis),
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Text(
                text = noticeSymbol(message.type),
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = ChatNoticeTextFormatter.title(message),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                ChatNoticeTextFormatter.body(message)?.let { body ->
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ChatNoticeTextFormatter.userMessage(message)?.let { userMessage ->
                    Text(
                        text = userMessage,
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notice.type.startsWith("shared_chat_")) {
                    Text(
                        text = "Shared Chat",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

internal fun noticeSymbol(type: ChatMessageType): String = when (type) {
    ChatMessageType.SUBSCRIPTION -> "★"
    ChatMessageType.RESUBSCRIPTION -> "↻"
    ChatMessageType.GIFT_SUBSCRIPTION -> "🎁"
    ChatMessageType.RAID -> "🚀"
    ChatMessageType.ANNOUNCEMENT -> "📣"
    else -> "•"
}

@Composable
internal fun noticeAccentColor(message: ChatMessage): Color = when (message.type) {
    ChatMessageType.SUBSCRIPTION,
    ChatMessageType.RESUBSCRIPTION -> MaterialTheme.colorScheme.tertiary

    ChatMessageType.GIFT_SUBSCRIPTION -> Color(0xFFE005B9)
    ChatMessageType.RAID -> MaterialTheme.colorScheme.primary
    ChatMessageType.ANNOUNCEMENT -> when (message.notice?.announcementColor?.uppercase()) {
        "BLUE" -> Color(0xFF3B82F6)
        "GREEN" -> Color(0xFF22C55E)
        "ORANGE" -> Color(0xFFF97316)
        "PURPLE" -> Color(0xFFA855F7)
        else -> MaterialTheme.colorScheme.primary
    }
    else -> MaterialTheme.colorScheme.secondary
}
