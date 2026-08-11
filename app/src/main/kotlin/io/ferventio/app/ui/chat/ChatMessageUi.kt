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
import io.ferventio.app.domain.BttvEmoteCompositionPlan
import io.ferventio.app.domain.BttvEmoteCompositionPlanner
import io.ferventio.app.domain.BttvModifierEffect
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
import io.ferventio.app.domain.ChatMentionParser
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


@Immutable
internal data class MessageRenderAssets(
    val badgeAssets: Map<String, ChatBadgeAsset>,
    val cheermoteAssets: Map<String, List<CheermoteAsset>>,
    val emoteCatalogByProviderAndId: Map<Pair<String, String>, ThirdPartyEmoteAsset>,
)

@Immutable
internal data class QuickModerationUiStrings(
    val banButton: String,
    val deleteButton: String,
    val banTitle: String,
    val banBody: String,
    val deleteTitle: String,
    val deleteBody: String,
    val cancel: String,
)

@Immutable
internal data class ImmutableBadgeAssetList(
    val items: List<ChatBadgeAsset>,
) {
    companion object {
        val Empty = ImmutableBadgeAssetList(emptyList())
    }
}

@Composable
internal fun IgnoredMessageRow(
    message: ChatMessage,
    mode: IgnoreDisplayMode,
    reason: String,
    showTimestamp: Boolean,
    onReveal: () -> Unit,
) {
    val masked = mode == IgnoreDisplayMode.TAP_TO_REVEAL
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clickable(onClick = onReveal),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (masked) 0.72f else 0.42f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (masked) 7.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTimestamp && !masked) {
                VerbatimText(
                    formatChatTimestamp(message.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (!masked) {
                VerbatimText(
                    message.userDisplayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
            }
            LocalizedText(
                if (masked) {
                    "Сообщение скрыто. Нажмите, чтобы показать"
                } else {
                    "Свернуто правилом: ${verbatimArgument(reason)}"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(
    message: ChatMessage,
    profile: TwitchUser?,
    hydratedUserColor: String? = null,
    showAvatar: Boolean,
    showBadges: Boolean,
    showTimestamp: Boolean,
    showQuickBanButton: Boolean = false,
    showQuickDeleteButton: Boolean = false,
    canModerate: Boolean = false,
    quickModerationStrings: QuickModerationUiStrings? = null,
    onQuickBan: (ChatMessage) -> Unit = {},
    onQuickDelete: (ChatMessage) -> Unit = {},
    showDeletedMessageContent: Boolean,
    animateEmotes: Boolean,
    emoteScalePercent: Int,
    messageDensity: MessageDensity,
    chatNameStyle: ChatNameStyle,
    wrapMessageLines: Boolean,
    mentionColorArgb: Long,
    renderAssets: MessageRenderAssets,
    frankerFaceZBadges: ImmutableBadgeAssetList = ImmutableBadgeAssetList.Empty,
    onOpenUser: (ChatMessage) -> Unit,
    onOpenEmote: (EmoteDisplayInfo) -> Unit,
    ownUserId: String? = null,
    highlighted: Boolean = false,
    decoration: MessageDecoration = MessageDecoration(),
    onNavigateToMessage: (String) -> Unit = {},
    onOpenReplyThread: (ChatMessage) -> Unit = {},
    onRetry: (ChatMessage) -> Unit = {},
    onOpenActions: (ChatMessage) -> Unit = {},
    onReply: (ChatMessage) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openUserProfileByLogin = LocalOpenUserProfileByLogin.current
    val moderationPreferences = rememberQuickModerationPreferenceState()
    val badgeAssets = renderAssets.badgeAssets
    val cheermoteAssets = renderAssets.cheermoteAssets
    val emoteCatalogByProviderAndId = renderAssets.emoteCatalogByProviderAndId
    val frankerFaceZBadgeItems = frankerFaceZBadges.items
    val deleted = message.isDeleted
    val isAnnouncement = message.type == ChatMessageType.ANNOUNCEMENT
    val isSystemLine = message.type in SYSTEM_MESSAGE_TYPES
    val resolvedUserColor = remember(message.userId, message.color, hydratedUserColor) {
        twitchUserColor(message.color ?: hydratedUserColor, message.userId)
    }
    val rowAlpha = if (deleted && showDeletedMessageContent) 0.42f else 1f
    val shownText = if (isAnnouncement) {
        message.notice?.userMessage
            ?.takeIf(String::isNotBlank)
            ?: message.text
    } else {
        ChatPresentationPolicy.visibleText(
            message = message,
            showDeletedMessageContent = showDeletedMessageContent,
        )
    }
    val canReply = (!message.isSystem || isAnnouncement) && !message.isDeleted && message.id.isNotBlank()
    val isReplyToOwn = ownUserId != null &&
        message.userId != ownUserId &&
        message.reply?.parentUserId == ownUserId
    val isBroadcaster = message.badges.any { badge -> badge.setId == "broadcaster" }
    val canQuickBan = showQuickBanButton &&
        canModerate &&
        !message.isDeleted &&
        !message.isSystem &&
        message.userId.isNotBlank() &&
        message.userId != ownUserId &&
        !isBroadcaster
    val canQuickDelete = showQuickDeleteButton &&
        canModerate &&
        !message.isDeleted &&
        !message.isSystem &&
        message.id.isNotBlank()
    val showQuickActionStrip = showTimestamp && quickModerationStrings != null && (canQuickBan || canQuickDelete)
    var pendingQuickAction by remember(message.id) { mutableStateOf<String?>(null) }

    var revealIgnored by remember(message.id, decoration.ignoreDisplayMode) { mutableStateOf(false) }
    if (decoration.isIgnored && !revealIgnored) {
        IgnoredMessageRow(
            message = message,
            mode = decoration.ignoreDisplayMode ?: IgnoreDisplayMode.HIDE,
            reason = decoration.ignoreReason.orEmpty(),
            showTimestamp = showTimestamp,
            onReveal = { revealIgnored = true },
        )
        return
    }

    if (message.notice != null && message.type in NOTICE_MESSAGE_TYPES && !isAnnouncement) {
        NoticeMessageRow(
            message = message,
            showTimestamp = showTimestamp,
            onOpenUser = { onOpenUser(message) },
            onOpenActions = { onOpenActions(message) },
        )
        return
    }

    if (isSystemLine) {
        SystemMessageRow(
            message = message,
            showTimestamp = showTimestamp,
            messageDensity = messageDensity,
        )
        return
    }

    val preparedText = remember(message) { ChatMessageTextPreparation.get(message) }
    val fragmentText = preparedText.fragmentText
    val useFragments = shownText == fragmentText && (!deleted || showDeletedMessageContent)
    val bttvComposition = remember(message.fragments, useFragments) {
        if (useFragments) BttvEmoteCompositionPlanner.build(message.fragments)
        else BttvEmoteCompositionPlan.Empty
    }
    val renderedGroups = remember(
        message.fragments,
        animateEmotes,
        useFragments,
        cheermoteAssets,
        emoteCatalogByProviderAndId,
        bttvComposition,
    ) {
        if (!useFragments) emptyList()
        else buildRenderedEmoteGroups(
            fragments = message.fragments,
            animateEmotes = animateEmotes,
            cheermoteAssets = cheermoteAssets,
            emoteCatalogByProviderAndId = emoteCatalogByProviderAndId,
            compositionPlan = bttvComposition,
        )
    }
    val renderedGroupsByBaseIndex = remember(renderedGroups) { renderedGroups.associateBy(RenderedEmoteGroup::baseIndex) }
    val highlightColorArgb = decoration.highlightColorArgb
    val skippedFragmentIndices = remember(renderedGroups, bttvComposition, message.fragments) {
        val allOverlayIndices = bttvComposition.groups
            .flatMap { group -> group.overlayFragmentIndices }
            .toSet()
        buildSet {
            // Modifier tokens and whitespace consumed by a valid prefix chain are
            // always hidden. Overlay tokens are hidden only after their image URL
            // resolved and they were actually merged into a rendered group.
            addAll(bttvComposition.hiddenFragmentIndices - allOverlayIndices)
            renderedGroups.forEach { group ->
                addAll(group.fragmentIndices.drop(1))
                if (BttvModifierEffect.NO_SPACE in group.effects) {
                    val trailingIndex = group.baseIndex + 1
                    val trailingText = message.fragments.getOrNull(trailingIndex) as? ChatFragment.Text
                    if (trailingText?.text?.isBlank() == true) add(trailingIndex)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .measureMessageRender(message.id)
            .alpha(rowAlpha)
            .padding(
                horizontal = 4.dp,
                vertical = when (messageDensity) {
                    MessageDensity.COMPACT -> 0.dp
                    MessageDensity.NORMAL -> 1.dp
                    MessageDensity.RELAXED -> 3.dp
                },
            )
            .background(
                color = when {
                    highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    highlightColorArgb != null -> colorFromArgb(highlightColorArgb).copy(alpha = 0.24f)
                    isReplyToOwn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                    isAnnouncement -> noticeAccentColor(message).copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 4.dp, vertical = if (isAnnouncement) 4.dp else 0.dp),
    ) {
        message.reply?.let { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (isReplyToOwn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    },
                )
                Spacer(Modifier.width(5.dp))
                VerbatimText(
                    text = preparedText.replyPreview.orEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToMessage(reply.parentMessageId) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isReplyToOwn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    },
                )
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = localizedString("Открыть ветку ответов"),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onOpenReplyThread(message) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val colorScheme = MaterialTheme.colorScheme
        val lineUserColor = if (message.isSystem && !isAnnouncement) colorScheme.secondary else resolvedUserColor
        val lineTimestampColor = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        val lineTextColor = if (deleted && !showDeletedMessageContent) {
            colorScheme.onSurfaceVariant
        } else {
            colorScheme.onSurface
        }
        val lineAccentColor = if (colorScheme.background.luminance() > 0.5f) {
            Color(0xFF005AC2)
        } else {
            Color(0xFF8AB4F8)
        }
        val line = remember(
            message,
            shownText,
            showTimestamp,
            showAvatar,
            showBadges,
            frankerFaceZBadgeItems.size,
            useFragments,
            renderedGroupsByBaseIndex,
            skippedFragmentIndices,
            lineUserColor,
            lineTimestampColor,
            lineTextColor,
            lineAccentColor,
            chatNameStyle,
            mentionColorArgb,
        ) {
            buildChatLine(
                message = message,
                shownText = shownText,
                showTimestamp = showTimestamp && !showQuickActionStrip,
                showAvatar = showAvatar,
                showBadges = showBadges,
                frankerFaceZBadgeCount = if (showBadges) frankerFaceZBadgeItems.size else 0,
                useFragments = useFragments,
                renderedGroupsByBaseIndex = renderedGroupsByBaseIndex,
                skippedFragmentIndices = skippedFragmentIndices,
                userColor = lineUserColor,
                timestampColor = lineTimestampColor,
                textColor = lineTextColor,
                accentColor = lineAccentColor,
                nameStyle = chatNameStyle,
                mentionColor = colorFromArgb(mentionColorArgb),
            )
        }
        val textLayoutResult = remember(
            message.id,
            shownText,
            showAvatar,
            showBadges,
            showTimestamp,
            renderedGroups,
        ) { TextLayoutResultHolder() }
        val emoteScale = emoteScalePercent.coerceIn(75, 200) / 100f
        val inlineContent = remember(
            showAvatar,
            profile?.profileImageUrl,
            message.author.profileImageUrl,
            message.userDisplayName,
            resolvedUserColor,
            showBadges,
            message.badges,
            badgeAssets,
            frankerFaceZBadges,
            renderedGroups,
            emoteScale,
        ) {
            buildMap<String, InlineTextContent> {
            if (showAvatar) {
                put(
                    CHAT_INLINE_AVATAR_ID,
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = 1.08.em,
                            height = 1.08.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        UserAvatar(
                            imageUrl = profile?.profileImageUrl ?: message.author.profileImageUrl,
                            displayName = message.userDisplayName,
                            color = resolvedUserColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
            if (showBadges) {
                message.badges.forEachIndexed { index, badge ->
                    put(
                        inlineBadgeId(index),
                        InlineTextContent(
                            placeholder = Placeholder(
                                width = 1.08.em,
                                height = 1.08.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            ),
                        ) {
                            ChatBadgeIcon(
                                badge = badge,
                                asset = ChatAssetResolver.badgeAsset(badge, badgeAssets),
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                }
                frankerFaceZBadgeItems.forEachIndexed { index, asset ->
                    put(
                        inlineFfzBadgeId(index),
                        InlineTextContent(
                            placeholder = Placeholder(
                                width = 1.08.em,
                                height = 1.08.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            ),
                        ) {
                            ChatBadgeAssetIcon(asset = asset, modifier = Modifier.fillMaxSize())
                        },
                    )
                }
            }
            renderedGroups.forEach { group ->
                val transform = BttvEmoteRenderTransformResolver.resolve(group.effects)
                put(
                    inlineFragmentId(group.baseIndex),
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = (1.32f * emoteScale * transform.widthMultiplier).em,
                            height = (1.32f * emoteScale).em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        LayeredEmoteImage(
                            urls = group.urls,
                            contentDescription = group.info.code,
                            modifier = Modifier
                                .fillMaxSize()
                                .bttvEmoteTransform(transform, animateDynamicEffects = animateEmotes),
                        )
                    },
                )
            }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (showQuickActionStrip) {
                VerbatimText(
                    formatChatTimestamp(message.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = lineTimestampColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(4.dp))
                if (canQuickBan) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = quickModerationStrings.banButton,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                if (moderationPreferences.confirmActions) {
                                    pendingQuickAction = "ban"
                                } else {
                                    onQuickBan(message)
                                }
                            },
                    )
                    Spacer(Modifier.width(3.dp))
                }
                if (canQuickDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = quickModerationStrings.deleteButton,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                if (moderationPreferences.confirmActions) {
                                    pendingQuickAction = "delete"
                                } else {
                                    onQuickDelete(message)
                                }
                            },
                    )
                    Spacer(Modifier.width(5.dp))
                }
            }
            VerbatimText(
                text = line.text,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(
                        message.id,
                        canReply,
                        line.text.text,
                        line.identityRanges,
                        line.emoteRanges,
                        line.linkRanges,
                    ) {
                        detectTapGestures(
                            onTap = { position ->
                                val offset = textLayoutResult.value?.getOffsetForPosition(position) ?: -1
                                val link = line.linkRanges.firstOrNull { offset in it.range }
                                val mention = ChatMentionParser.findAt(line.text.text, offset)
                                when {
                                    link != null -> runCatching { uriHandler.openUri(link.url) }
                                    mention != null -> openUserProfileByLogin(message.channelId, mention.login)
                                    line.identityRanges.any { offset in it } -> onOpenUser(message)
                                    canReply -> onReply(message)
                                }
                            },
                            onLongPress = { position ->
                                val offset = textLayoutResult.value?.getOffsetForPosition(position) ?: -1
                                val emote = line.emoteRanges.firstOrNull { offset in it.range }?.info
                                if (emote != null) onOpenEmote(emote) else onOpenActions(message)
                            },
                        )
                    },
                inlineContent = inlineContent,
                onTextLayout = { textLayoutResult.value = it },
                maxLines = if (wrapMessageLines) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = when (messageDensity) {
                        MessageDensity.COMPACT -> 1.15.em
                        MessageDensity.NORMAL -> 1.35.em
                        MessageDensity.RELAXED -> 1.55.em
                    },
                ),
            )
        }

        quickModerationStrings?.let { strings ->
            when (pendingQuickAction) {
                "ban" -> AlertDialog(
                    onDismissRequest = { pendingQuickAction = null },
                    title = { LocalizedText(strings.banTitle) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LocalizedText(strings.banBody)
                            VerbatimText("@${message.userLogin.ifBlank { message.userDisplayName }}", fontWeight = FontWeight.Bold)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingQuickAction = null
                            onQuickBan(message)
                        }) { LocalizedText(strings.banButton) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingQuickAction = null }) { LocalizedText(strings.cancel) }
                    },
                )
                "delete" -> AlertDialog(
                    onDismissRequest = { pendingQuickAction = null },
                    title = { LocalizedText(strings.deleteTitle) },
                    text = { LocalizedText(strings.deleteBody) },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingQuickAction = null
                            onQuickDelete(message)
                        }) { LocalizedText(strings.deleteButton) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingQuickAction = null }) { LocalizedText(strings.cancel) }
                    },
                )
            }
        }
        if (message.outgoingState != OutgoingMessageState.NONE) {
            OutgoingMessageStatus(
                state = message.outgoingState,
                error = message.outgoingError,
                onRetry = { onRetry(message) },
            )
        }
    }
}

internal data class EmoteDisplayInfo(
    val code: String,
    val provider: String,
    val id: String,
    val urls: List<String>,
    val animated: Boolean,
    val zeroWidth: Boolean,
    val scope: EmoteScope? = null,
    val ownerName: String? = null,
    val imageType: String? = null,
    val sourceUrl: String? = null,
    val accentColor: Color? = null,
    val layerCount: Int = urls.size,
)

internal data class RenderedEmoteGroup(
    val baseIndex: Int,
    val fragmentIndices: List<Int>,
    val urls: List<String>,
    val info: EmoteDisplayInfo,
    val effects: Set<BttvModifierEffect> = emptySet(),
)

internal data class EmoteTextRange(
    val range: IntRange,
    val info: EmoteDisplayInfo,
)

internal data class ChatLinkTextRange(
    val range: IntRange,
    val url: String,
)

internal data class ChatLineLayout(
    val text: AnnotatedString,
    val identityRanges: List<IntRange>,
    val emoteRanges: List<EmoteTextRange>,
    val linkRanges: List<ChatLinkTextRange>,
)

internal class TextLayoutResultHolder(
    var value: TextLayoutResult? = null,
)

internal fun buildRenderedEmoteGroups(
    fragments: List<ChatFragment>,
    animateEmotes: Boolean,
    cheermoteAssets: Map<String, List<CheermoteAsset>>,
    emoteCatalogByProviderAndId: Map<Pair<String, String>, ThirdPartyEmoteAsset>,
    compositionPlan: BttvEmoteCompositionPlan = BttvEmoteCompositionPlanner.build(fragments),
): List<RenderedEmoteGroup> = compositionPlan.groups.mapNotNull { group ->
    val baseFragment = fragments.getOrNull(group.baseFragmentIndex) ?: return@mapNotNull null
    val base = resolveFragmentEmote(
        fragment = baseFragment,
        animateEmotes = animateEmotes,
        cheermoteAssets = cheermoteAssets,
        catalogByProviderAndId = emoteCatalogByProviderAndId,
    ) ?: return@mapNotNull null

    val resolvedOverlays = group.overlayFragmentIndices.mapNotNull { overlayIndex ->
        val overlayFragment = fragments.getOrNull(overlayIndex) ?: return@mapNotNull null
        resolveFragmentEmote(
            fragment = overlayFragment,
            animateEmotes = animateEmotes,
            cheermoteAssets = cheermoteAssets,
            catalogByProviderAndId = emoteCatalogByProviderAndId,
        )?.let { overlayIndex to it }
    }
    val combinedUrls = base.urls + resolvedOverlays.flatMap { (_, overlay) -> overlay.urls }
    val layerInfos = listOf(base) + resolvedOverlays.map { it.second }

    RenderedEmoteGroup(
        baseIndex = group.baseFragmentIndex,
        fragmentIndices = listOf(group.baseFragmentIndex) + resolvedOverlays.map { it.first },
        urls = combinedUrls,
        info = base.copy(
            code = layerInfos.joinToString(" + ") { it.code },
            provider = layerInfos
                .map(EmoteDisplayInfo::provider)
                .distinct()
                .singleOrNull()
                ?: "composite",
            id = layerInfos.joinToString("+") { it.id },
            urls = combinedUrls,
            zeroWidth = resolvedOverlays.isNotEmpty(),
            layerCount = combinedUrls.size,
        ),
        effects = group.effects,
    )
}

internal fun resolveFragmentEmote(
    fragment: ChatFragment,
    animateEmotes: Boolean,
    cheermoteAssets: Map<String, List<CheermoteAsset>>,
    catalogByProviderAndId: Map<Pair<String, String>, ThirdPartyEmoteAsset>,
): EmoteDisplayInfo? = when (fragment) {
    is ChatFragment.TwitchEmote -> ChatAssetResolver.twitchEmoteUrl(
        fragment = fragment,
        animate = animateEmotes,
    )?.let { url ->
        EmoteDisplayInfo(
            code = fragment.text,
            provider = "twitch",
            id = fragment.emoteId,
            urls = listOf(url),
            animated = animateEmotes && "animated" in fragment.formats,
            zeroWidth = false,
            sourceUrl = "https://www.twitch.tv/",
        )
    }

    is ChatFragment.ThirdPartyEmote -> {
        val asset = catalogByProviderAndId[fragment.provider to fragment.emoteId]
        val url = when {
            !animateEmotes && fragment.animated -> asset?.imageUrl1x
            else -> fragment.imageUrl ?: asset?.imageUrl2x ?: asset?.imageUrl1x
        }
        url?.takeIf(String::isNotBlank)?.let {
            EmoteDisplayInfo(
                code = fragment.text,
                provider = fragment.provider,
                id = fragment.emoteId,
                urls = listOf(it),
                animated = fragment.animated,
                zeroWidth = fragment.zeroWidth,
                scope = asset?.scope,
                ownerName = asset?.ownerName,
                imageType = asset?.imageType,
                sourceUrl = asset?.sourceUrl,
            )
        }
    }

    is ChatFragment.Gif -> fragment.url.takeIf { animateEmotes && it.isNotBlank() }?.let {
        EmoteDisplayInfo(
            code = fragment.text,
            provider = "gif",
            id = fragment.gifId,
            urls = listOf(it),
            animated = true,
            zeroWidth = false,
        )
    }

    is ChatFragment.Cheermote -> CheermoteResolver.resolve(
        prefix = fragment.prefix,
        bits = fragment.bits,
        animate = animateEmotes,
        assetsByPrefix = cheermoteAssets,
    )?.let { asset ->
        asset.imageUrl(animateEmotes)?.let { url ->
            EmoteDisplayInfo(
                code = fragment.text,
                provider = "twitch-cheermote",
                id = "${fragment.prefix}:${asset.tier}",
                urls = listOf(url),
                animated = animateEmotes && !asset.animatedImageUrl.isNullOrBlank(),
                zeroWidth = false,
                sourceUrl = "https://www.twitch.tv/",
                accentColor = parseHexColor(asset.color),
            )
        }
    }

    else -> null
}