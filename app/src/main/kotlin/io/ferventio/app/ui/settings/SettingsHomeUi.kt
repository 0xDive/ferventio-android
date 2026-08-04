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
internal fun SettingsHomePage(
    state: FerventioUiState,
    pushState: PushUiState,
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onOpen: (SettingsPage) -> Unit,
    onLogout: () -> Unit,
) {
    val profile = state.session?.userId?.let(state.userProfilesById::get)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag(SETTINGS_HOME_LIST_TEST_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "settings-profile") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(SettingsPage.ACCOUNT) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!profile?.profileImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.extraLarge),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.displayName
                                ?: state.session?.login ?: strings.noAccount,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.session?.login?.let { "@$it" }
                                ?: if (state.isAuthenticated) strings.twitchAccount else strings.readOnlyChats,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onLogout) {
                        Text(if (state.isAuthenticated) strings.signOut else strings.signIn)
                    }
                }
            }
        }

        item(key = "settings-chat") {
            SettingsHomeGroup(strings.settingsChatGroup) {
                SettingsMenuRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = strings.messagesAndAppearance,
                    summary = strings.messagesAndAppearanceSummary,
                    onClick = { onOpen(SettingsPage.APPEARANCE) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.Tune,
                    title = strings.inputAndBehavior,
                    summary = strings.inputAndBehaviorSummary,
                    onClick = { onOpen(SettingsPage.CHAT) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.Person,
                    title = strings.userCard,
                    summary = strings.userCardSummary,
                    onClick = { onOpen(SettingsPage.USER_CARD) },
                )
            }
        }

        item(key = "settings-features") {
            SettingsHomeGroup(strings.settingsFeaturesGroup) {
                SettingsMenuRow(
                    icon = Icons.Default.Palette,
                    title = strings.highlights,
                    summary = strings.highlightsSummary,
                    onClick = { onOpen(SettingsPage.HIGHLIGHTS) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.Block,
                    title = strings.ignore,
                    summary = strings.ignoreSummary,
                    onClick = { onOpen(SettingsPage.IGNORE) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.FilterAlt,
                    title = strings.filterLanguage,
                    summary = strings.filterLanguageSummary,
                    onClick = { onOpen(SettingsPage.FILTERS) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.Notifications,
                    title = strings.notifications,
                    summary = pushStatusLabel(pushState),
                    onClick = { onOpen(SettingsPage.NOTIFICATIONS) },
                )
            }
        }

        item(key = "settings-data") {
            SettingsHomeGroup(strings.settingsDataGroup) {
                SettingsMenuRow(
                    icon = Icons.Default.History,
                    title = strings.localHistory,
                    summary = strings.localHistorySummary,
                    onClick = { onOpen(SettingsPage.HISTORY) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.DeleteSweep,
                    title = strings.imageCache,
                    summary = strings.imageCacheSummary,
                    onClick = { onOpen(SettingsPage.IMAGE_CACHE) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.ContentCopy,
                    title = strings.exportAndSync,
                    summary = if (state.settingsSyncEnabled) {
                        "Ревизия ${state.settingsSyncRevision} · ${settingsSyncStatusLabel(state.settingsSyncStatus)}"
                    } else {
                        strings.exportAndSyncSummary
                    },
                    onClick = { onOpen(SettingsPage.BACKUP_SYNC) },
                )
            }
        }

        item(key = "settings-app") {
            SettingsHomeGroup(strings.settingsAppGroup) {
                SettingsMenuRow(
                    icon = Icons.Default.Translate,
                    title = strings.appLanguage,
                    summary = appLanguageLabel(state.appLanguage, strings),
                    onClick = { onOpen(SettingsPage.LANGUAGE) },
                )
                SettingsGroupDivider()
                SettingsMenuRow(
                    icon = Icons.Default.Info,
                    title = strings.aboutTitle,
                    summary = "Ferventio ${BuildConfig.VERSION_NAME}",
                    onClick = { onOpen(SettingsPage.ABOUT) },
                )
            }
        }
    }
}

internal fun formatLocalizedString(template: String, vararg args: Any): String =
    String.format(java.util.Locale.ROOT, template, *args)

internal fun formatLocalizedDuration(seconds: Int, strings: AppStrings): String = when {
    seconds <= 0 -> "—"
    seconds % 86_400 == 0 -> formatLocalizedString(strings.durationDaysFormat, seconds / 86_400)
    seconds % 3_600 == 0 -> formatLocalizedString(strings.durationHoursFormat, seconds / 3_600)
    seconds % 60 == 0 -> formatLocalizedString(strings.durationMinutesFormat, seconds / 60)
    else -> formatLocalizedString(strings.durationSecondsFormat, seconds)
}

internal fun moderationActionLabel(actionId: String, strings: AppStrings): String = when {
    actionId.startsWith("timeout:") -> actionId.substringAfter(':').toIntOrNull()
        ?.let { seconds -> "${strings.moderationTimeout} ${formatLocalizedDuration(seconds, strings)}" }
        ?: strings.moderationTimeout
    actionId == "warn" -> strings.moderationWarn
    actionId == "ban" -> strings.moderationBan
    actionId == "unban" -> strings.moderationUnban
    else -> actionId
}

internal fun appLanguageLabel(language: AppLanguage, strings: AppStrings): String = when (language) {
    AppLanguage.SYSTEM -> strings.languageSystem
    AppLanguage.RUSSIAN -> strings.languageRussian
    AppLanguage.ENGLISH -> strings.languageEnglish
}

internal fun appLanguageSummary(language: AppLanguage, strings: AppStrings): String = when (language) {
    AppLanguage.SYSTEM -> strings.languageSystemSummary
    AppLanguage.RUSSIAN -> strings.languageRussianSummary
    AppLanguage.ENGLISH -> strings.languageEnglishSummary
}

internal fun languageSearchText(language: AppLanguage, strings: AppStrings): String = buildString {
    append(appLanguageLabel(language, strings))
    append(' ')
    append(appLanguageSummary(language, strings))
    append(' ')
    append(language.storageValue)
    when (language) {
        AppLanguage.SYSTEM -> append(" automatic device")
        AppLanguage.RUSSIAN -> append(" русский russian ru")
        AppLanguage.ENGLISH -> append(" english английский en")
    }
}

@Composable
internal fun LanguageSelectionRow(
    language: AppLanguage,
    strings: AppStrings,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(Modifier.weight(1f)) {
            Text(
                appLanguageLabel(language, strings),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                appLanguageSummary(language, strings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsHomeGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            title,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

internal data class AboutLinkItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val summary: String,
    val url: String,
)

internal fun configuredAboutLinks(strings: AppStrings): List<AboutLinkItem> = buildList {
    if (BuildConfig.APP_WEBSITE_URL.isNotBlank()) {
        add(AboutLinkItem(Icons.Default.Public, strings.website, strings.websiteSummary, BuildConfig.APP_WEBSITE_URL))
    }
    if (BuildConfig.APP_GITHUB_URL.isNotBlank()) {
        add(AboutLinkItem(Icons.Default.Code, strings.github, strings.githubSummary, BuildConfig.APP_GITHUB_URL))
    }
    if (BuildConfig.APP_TELEGRAM_CHANNEL_URL.isNotBlank()) {
        add(AboutLinkItem(Icons.Default.Forum, strings.telegramChannel, strings.telegramChannelSummary, BuildConfig.APP_TELEGRAM_CHANNEL_URL))
    }
    if (BuildConfig.APP_TELEGRAM_CHAT_URL.isNotBlank()) {
        add(AboutLinkItem(Icons.AutoMirrored.Filled.Chat, strings.telegramChat, strings.telegramChatSummary, BuildConfig.APP_TELEGRAM_CHAT_URL))
    }
    if (BuildConfig.APP_TRANSLATIONS_URL.isNotBlank()) {
        add(AboutLinkItem(Icons.Default.Translate, strings.translationProject, strings.translationProjectSummary, BuildConfig.APP_TRANSLATIONS_URL))
    }
}

@Composable
internal fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceButtons(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            val modifier = Modifier
                .widthIn(min = 84.dp)
                .heightIn(min = 48.dp)
            if (value == selected) {
                FilledTonalButton(onClick = { onSelect(value) }, modifier = modifier) {
                    Text(label(value), maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = modifier) {
                    Text(label(value), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun MentionColorPicker(
    selectedArgb: Long,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MentionColors.presets.forEach { argb ->
            val selected = argb == selectedArgb
            val color = colorFromArgb(argb)
            Surface(
                modifier = Modifier
                    .size(if (selected) 38.dp else 34.dp)
                    .clickable { onSelect(argb) },
                shape = MaterialTheme.shapes.extraLarge,
                color = color,
                border = BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                ),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Выбранный цвет",
                            tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

internal fun settingsSyncStatusLabel(status: SettingsSyncStatus): String = when (status) {
    SettingsSyncStatus.DISABLED -> "Отключена"
    SettingsSyncStatus.IDLE -> "Синхронизировано"
    SettingsSyncStatus.SYNCING -> "Синхронизация…"
    SettingsSyncStatus.CONFLICT -> "Нужно разрешить конфликт"
    SettingsSyncStatus.ERROR -> "Ошибка"
}

@Composable
internal fun settingsSyncStatusColor(status: SettingsSyncStatus): Color = when (status) {
    SettingsSyncStatus.IDLE -> MaterialTheme.colorScheme.primary
    SettingsSyncStatus.SYNCING -> MaterialTheme.colorScheme.tertiary
    SettingsSyncStatus.CONFLICT, SettingsSyncStatus.ERROR -> MaterialTheme.colorScheme.error
    SettingsSyncStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun formatSyncTimestamp(value: String): String = runCatching {
    Instant.parse(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}.getOrDefault(value)

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            content()
        }
    }
}

internal fun formatPushTime(timestampMillis: Long): String =
    java.time.Instant.ofEpochMilli(timestampMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))

internal fun pushStatusLabel(state: PushUiState): String = when (state.status) {
    PushStatus.DISABLED -> state.detail.ifBlank { "Отключено" }
    PushStatus.NEEDS_CONFIGURATION -> state.detail.ifBlank { "Требуется настройка" }
    PushStatus.REGISTERING -> state.detail.ifBlank { "Регистрация…" }
    PushStatus.CONNECTING -> state.detail.ifBlank { "Подключение…" }
    PushStatus.ACTIVE -> state.detail.ifBlank { "Подключено" }
    PushStatus.TEMPORARILY_UNAVAILABLE -> state.detail.ifBlank { "Временно недоступно" }
    PushStatus.ERROR -> state.detail.ifBlank { "Ошибка" }
}

@Composable
internal fun pushStatusColor(status: PushStatus): Color = when (status) {
    PushStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    PushStatus.ERROR -> MaterialTheme.colorScheme.error
    PushStatus.TEMPORARILY_UNAVAILABLE -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
