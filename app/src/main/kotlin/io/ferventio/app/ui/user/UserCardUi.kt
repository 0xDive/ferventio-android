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
internal fun UserCardDragHandle(
    expanded: Boolean,
    onExpand: () -> Unit,
    onPartialExpand: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(expanded) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                    },
                    onDragEnd = {
                        when {
                            accumulatedDrag < -24f -> onExpand()
                            accumulatedDrag > 24f -> onPartialExpand()
                        }
                        accumulatedDrag = 0f
                    },
                    onDragCancel = { accumulatedDrag = 0f },
                )
            }
            .clickable {
                if (expanded) onPartialExpand() else onExpand()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        ) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserCardSheet(
    cardState: UserCardUiState,
    isAuthenticated: Boolean,
    showTimestamp: Boolean,
    showBadges: Boolean,
    showDeletedMessageContent: Boolean,
    animateEmotes: Boolean,
    emoteScalePercent: Int,
    messageDensity: MessageDensity,
    chatNameStyle: ChatNameStyle,
    wrapMessageLines: Boolean,
    mentionColorArgb: Long,
    badgeAssetsByChannel: Map<String, Map<String, ChatBadgeAsset>>,
    frankerFaceZBadgesByUserId: Map<String, List<ChatBadgeAsset>>,
    frankerFaceZChannelBadgesByChannel: Map<String, Map<String, List<ChatBadgeAsset>>>,
    cheermoteAssetsByChannel: Map<String, Map<String, List<CheermoteAsset>>>,
    emoteCatalogByChannel: Map<String, List<ThirdPartyEmoteAsset>>,
    timeoutPresetsSeconds: List<Int>,
    showBanAction: Boolean,
    moderationActionOrder: List<String>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onMention: () -> Unit,
    onTimeout: (String, String, String, Int) -> Unit,
    onBan: (String, String, String) -> Unit,
    onUnban: (String, String, String) -> Unit,
    onWarn: (String, String, String, String) -> Unit,
    onBlock: (String, String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetScope = rememberCoroutineScope()
    var emoteDetails by remember { mutableStateOf<EmoteDisplayInfo?>(null) }
    var showWarnDialog by rememberSaveable { mutableStateOf(false) }
    var warnReason by rememberSaveable { mutableStateOf("") }
    var pendingDangerAction by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    @Suppress("DEPRECATION") // LocalClipboard migration requires suspend clipboard writes.
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val userCardStrings = rememberAppStrings(appLanguage)
    val recentListState = rememberLazyListState()
    val recentListDragged by recentListState.interactionSource.collectIsDraggedAsState()
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    LaunchedEffect(recentListDragged) {
        if (recentListDragged) hideKeyboard()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Content gestures must never move or dismiss the sheet. Only the dedicated
        // handle below can switch between partial and expanded states.
        sheetGesturesEnabled = false,
        dragHandle = {
            UserCardDragHandle(
                expanded = sheetState.currentValue == SheetValue.Expanded,
                onExpand = {
                    sheetScope.launch { sheetState.expand() }
                },
                onPartialExpand = {
                    sheetScope.launch { sheetState.partialExpand() }
                },
            )
        },
    ) {
        val data = cardState.data
        val userCardEmoteCatalogByProviderAndId = remember(data?.channelId, emoteCatalogByChannel) {
            data?.channelId
                ?.let(emoteCatalogByChannel::get)
                .orEmpty()
                .associateBy { asset -> asset.provider to asset.id }
        }
        if (data == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (cardState.isLoading) CircularProgressIndicator()
                else Text(cardState.errorMessage ?: "Нет данных")
            }
            return@ModalBottomSheet
        }

        val userCardRenderAssets = remember(
            data.channelId,
            badgeAssetsByChannel,
            cheermoteAssetsByChannel,
            userCardEmoteCatalogByProviderAndId,
        ) {
            MessageRenderAssets(
                badgeAssets = badgeAssetsByChannel[data.channelId].orEmpty(),
                cheermoteAssets = cheermoteAssetsByChannel[data.channelId].orEmpty(),
                emoteCatalogByProviderAndId = userCardEmoteCatalogByProviderAndId,
            )
        }
        val userCardFfzBadgesByUser = remember(
            data.channelId,
            frankerFaceZBadgesByUserId,
            frankerFaceZChannelBadgesByChannel,
        ) {
            val channelBadges = frankerFaceZChannelBadgesByChannel[data.channelId].orEmpty()
            (frankerFaceZBadgesByUserId.keys + channelBadges.keys).associateWith { userId ->
                ImmutableBadgeAssetList(
                    (frankerFaceZBadgesByUserId[userId].orEmpty() + channelBadges[userId].orEmpty())
                        .distinctBy(ChatBadgeAsset::key),
                )
            }
        }

        val color = twitchUserColor(
            data.recentMessages.firstOrNull()?.color,
            data.user.id,
        )
        val roleLabel = when (data.role) {
            io.ferventio.app.domain.ChannelUserRole.BROADCASTER -> "Владелец канала"
            io.ferventio.app.domain.ChannelUserRole.MODERATOR -> "Модератор"
            io.ferventio.app.domain.ChannelUserRole.VIP -> "VIP"
            io.ferventio.app.domain.ChannelUserRole.SUBSCRIBER -> "Подписчик"
            io.ferventio.app.domain.ChannelUserRole.VIEWER -> "Зритель"
        }
        val profileMessageBadges = data.recentMessages.lastOrNull { it.badges.isNotEmpty() }?.badges.orEmpty()
        val profileFfzBadges = (
            frankerFaceZBadgesByUserId[data.user.id].orEmpty() +
                frankerFaceZChannelBadgesByChannel[data.channelId]?.get(data.user.id).orEmpty()
            ).distinctBy(ChatBadgeAsset::key)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = !recentListDragged,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        imageUrl = data.user.profileImageUrl,
                        displayName = data.user.displayName,
                        color = color,
                        modifier = Modifier.size(76.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            data.user.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "@${data.user.login}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("ID: ${data.user.id}", style = MaterialTheme.typography.bodySmall)
                        Text(roleLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        if (showBadges && (profileMessageBadges.isNotEmpty() || profileFfzBadges.isNotEmpty())) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(top = 5.dp),
                            ) {
                                items(profileMessageBadges, key = { "${it.setId}/${it.id}" }) { badge ->
                                    ChatBadgeIcon(
                                        badge = badge,
                                        asset = ChatAssetResolver.badgeAsset(
                                            badge,
                                            badgeAssetsByChannel[data.channelId].orEmpty(),
                                        ),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                items(profileFfzBadges, key = { it.key }) { asset ->
                                    ChatBadgeAssetIcon(asset = asset, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    if (cardState.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }

            item(key = "profile-details") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        data.user.createdAt?.let {
                            Text("Аккаунт создан: ${formatProfileDate(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                        data.followerInfo.followedAt?.let { followedAt ->
                            Text(
                                "Фолловит с ${formatProfileDate(followedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            data.subscriptionStatusHidden -> Text(
                                "Данные подписки скрыты пользователем",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            data.subscriberMonths != null -> Text(
                                buildString {
                                    append(
                                        if (data.isCurrentlySubscribed == false) {
                                            "Ранее подписывался: "
                                        } else {
                                            "Подписка: "
                                        },
                                    )
                                    append(data.subscriberMonths)
                                    append(" мес.")
                                    data.subscriberTier?.let { tier -> append(", tier ").append(tier) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            data.isCurrentlySubscribed == true -> Text(
                                "Активная подписка",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            cardState.errorMessage?.let { error ->
                item(key = "profile-error") { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            item(key = "profile-actions") {
                Text("Действия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = onReply,
                            enabled = isAuthenticated && (data.sourceMessageId != null || data.recentMessages.isNotEmpty()),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Ответить")
                        }
                    }
                    item {
                        FilledTonalButton(onClick = onMention, enabled = isAuthenticated) {
                            Text("@")
                            Spacer(Modifier.width(5.dp))
                            Text("Упомянуть")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(data.user.login)) },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Копировать")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { uriHandler.openUri("https://www.twitch.tv/${data.user.login}") },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Twitch")
                        }
                    }
                    if (isAuthenticated) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    pendingDangerAction = "block"
                                },
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    userCardStrings.moderationBlock,
                                )
                            }
                        }
                    }
                }
            }

            if (ChatPresentationPolicy.shouldShowModeratorActions(isAuthenticated, data.canModerate)) {
                item(key = "moderation-actions") {
                    val allowedIds = remember(timeoutPresetsSeconds, showBanAction) {
                        buildList {
                            timeoutPresetsSeconds.distinct().forEach { add("timeout:$it") }
                            add("warn")
                            if (showBanAction) add("ban")
                            add("unban")
                        }
                    }
                    val orderedIds = remember(moderationActionOrder, allowedIds) {
                        (moderationActionOrder.filter { it in allowedIds } + allowedIds).distinct()
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text(
                                userCardStrings.moderationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(orderedIds, key = { it }) { actionId ->
                                    when {
                                        actionId.startsWith("timeout:") -> {
                                            val seconds = actionId.substringAfter(':').toIntOrNull()
                                            if (seconds != null) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        onTimeout(data.channelId, data.user.id, data.user.login, seconds)
                                                    },
                                                ) {
                                                    Icon(Icons.Default.Timer, contentDescription = null)
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(formatLocalizedDuration(seconds, userCardStrings))
                                                }
                                            }
                                        }

                                        actionId == "warn" -> OutlinedButton(onClick = { showWarnDialog = true }) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null)
                                            Spacer(Modifier.width(5.dp))
                                            Text(userCardStrings.moderationWarn)
                                        }

                                        actionId == "ban" -> Button(
                                            onClick = { pendingDangerAction = "ban" },
                                        ) {
                                            Icon(Icons.Default.Block, contentDescription = null)
                                            Spacer(Modifier.width(5.dp))
                                            Text(userCardStrings.moderationBan)
                                        }

                                        actionId == "unban" -> OutlinedButton(
                                            onClick = { pendingDangerAction = "unban" },
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null)
                                            Spacer(Modifier.width(5.dp))
                                            Text(userCardStrings.moderationUnban)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "recent-title") {
                Text("Последние сообщения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (data.recentMessages.isEmpty()) {
                item(key = "recent-empty") {
                    Text("В локальной истории сообщений нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item(key = "recent-messages") {
                    val recentMessagesNewestFirst = remember(data.user.id, data.recentMessages) {
                        data.recentMessages.asReversed()
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        LazyColumn(
                            state = recentListState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 4.dp,
                                vertical = 6.dp,
                            ),
                        ) {
                            items(
                                items = recentMessagesNewestFirst,
                                key = { it.id },
                                contentType = { it.type.name },
                            ) { message ->
                                MessageRow(
                                    message = message,
                                    profile = data.user,
                                    showAvatar = false,
                                    showBadges = showBadges,
                                    showTimestamp = showTimestamp,
                                    showDeletedMessageContent = showDeletedMessageContent,
                                    animateEmotes = animateEmotes,
                                    emoteScalePercent = emoteScalePercent,
                                    messageDensity = messageDensity,
                                    chatNameStyle = chatNameStyle,
                                    wrapMessageLines = wrapMessageLines,
                                    mentionColorArgb = mentionColorArgb,
                                    renderAssets = userCardRenderAssets,
                                    frankerFaceZBadges = userCardFfzBadgesByUser[message.userId]
                                        ?: ImmutableBadgeAssetList.Empty,
                                    onOpenUser = {},
                                    onOpenEmote = {
                                        hideKeyboard()
                                        emoteDetails = it
                                    },
                                    onReply = {},
                                )
                            }
                        }
                    }
                    Text(
                        "Новые сообщения находятся снизу. Прокручивай вверх, чтобы увидеть более старые.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    pendingDangerAction?.let { action ->
        val data = cardState.data
        if (data != null) {
            val title = when (action) {
                "ban" -> formatLocalizedString(userCardStrings.moderationBanTitleFormat, data.user.login)
                "unban" -> formatLocalizedString(userCardStrings.moderationUnbanTitleFormat, data.user.login)
                else -> formatLocalizedString(userCardStrings.moderationBlockTitleFormat, data.user.login)
            }
            val body = when (action) {
                "ban" -> userCardStrings.moderationBanBody
                "unban" -> userCardStrings.moderationUnbanBody
                else -> userCardStrings.moderationBlockBody
            }
            AlertDialog(
                onDismissRequest = { pendingDangerAction = null },
                title = { Text(title) },
                text = { Text(body) },
                confirmButton = {
                    TextButton(onClick = {
                        when (action) {
                            "ban" -> onBan(data.channelId, data.user.id, data.user.login)
                            "unban" -> onUnban(data.channelId, data.user.id, data.user.login)
                            else -> onBlock(data.channelId, data.user.id, data.user.login)
                        }
                        pendingDangerAction = null
                    }) {
                        Text(userCardStrings.moderationConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDangerAction = null }) {
                        Text(userCardStrings.moderationCancel)
                    }
                },
            )
        }
    }

    if (showWarnDialog) {
        val data = cardState.data
        AlertDialog(
            onDismissRequest = {
                showWarnDialog = false
                warnReason = ""
            },
            title = {
                Text(
                    formatLocalizedString(
                        userCardStrings.moderationWarnTitleFormat,
                        data?.user?.login.orEmpty(),
                    ),
                )
            },
            text = {
                OutlinedTextField(
                    value = warnReason,
                    onValueChange = { warnReason = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(userCardStrings.moderationReason) },
                    supportingText = {
                        Text(userCardStrings.moderationWarnSupportingText)
                    },
                    minLines = 2,
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val current = cardState.data ?: return@TextButton
                        val reason = warnReason.trim()
                        if (reason.isNotEmpty()) {
                            onWarn(current.channelId, current.user.id, current.user.login, reason)
                            showWarnDialog = false
                            warnReason = ""
                        }
                    },
                    enabled = warnReason.isNotBlank(),
                ) { Text(userCardStrings.moderationWarn) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWarnDialog = false
                    warnReason = ""
                }) { Text(userCardStrings.moderationCancel) }
            },
        )
    }

    emoteDetails?.let { info ->
        EmoteDetailsDialog(info = info, onDismiss = { emoteDetails = null })
    }
}
