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
import io.ferventio.app.R
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


internal enum class SettingsPage {
    ROOT,
    ACCOUNT,
    APPEARANCE,
    CHAT,
    USER_CARD,
    HISTORY,
    IMAGE_CACHE,
    BACKUP_SYNC,
    NOTIFICATIONS,
    HIGHLIGHTS,
    IGNORE,
    FILTERS,
    ADVANCED,
    ABOUT,
    LANGUAGE,
    PRIVACY,
    LICENSES,
}

internal fun SettingsPage.title(strings: AppStrings): String = when (this) {
    SettingsPage.ROOT -> strings.settingsTitle
    SettingsPage.ACCOUNT -> strings.twitchAccount
    SettingsPage.APPEARANCE -> strings.messagesAndAppearance
    SettingsPage.CHAT -> strings.inputAndBehavior
    SettingsPage.USER_CARD -> strings.userCard
    SettingsPage.HISTORY -> strings.localHistory
    SettingsPage.IMAGE_CACHE -> strings.imageCache
    SettingsPage.BACKUP_SYNC -> strings.exportAndSync
    SettingsPage.NOTIFICATIONS -> strings.notifications
    SettingsPage.HIGHLIGHTS -> strings.highlights
    SettingsPage.IGNORE -> strings.ignore
    SettingsPage.FILTERS -> strings.filterLanguage
    SettingsPage.ADVANCED -> strings.diagnostics
    SettingsPage.ABOUT -> strings.aboutTitle
    SettingsPage.LANGUAGE -> strings.languagePageTitle
    SettingsPage.PRIVACY -> strings.privacyTitle
    SettingsPage.LICENSES -> strings.licensesTitle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: FerventioUiState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
    pushState: PushUiState,
    onTestPush: () -> Unit,
    onReconnectPush: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onExportCrashReports: () -> Unit,
    onClearCrashReports: () -> Unit,
    onBack: () -> Unit,
) {
    @Suppress("DEPRECATION") // LocalClipboard migration requires suspend clipboard writes.
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val settingsListState = rememberLazyListState()
    val settingsListDragged by settingsListState.interactionSource.collectIsDraggedAsState()
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClearCrashReports by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeDevice by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeAllSessions by rememberSaveable { mutableStateOf(false) }
    var userCardTimeoutInput by rememberSaveable { mutableStateOf("") }
    var languageQuery by rememberSaveable { mutableStateOf("") }
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val strings = rememberAppStrings(state.appLanguage)
    val resourceStrings = rememberAppResourceStrings(state.appLanguage)
    val diagnosticsCopiedText = localizedString("Диагностика скопирована")

    LaunchedEffect(settingsListDragged) {
        if (settingsListDragged) hideKeyboard()
    }
    LaunchedEffect(page) {
        settingsListState.scrollToItem(0)
    }
    val navigateBack = {
        hideKeyboard()
        when (page) {
            SettingsPage.ROOT -> onBack()
            SettingsPage.LANGUAGE -> page = SettingsPage.ROOT
            SettingsPage.PRIVACY, SettingsPage.LICENSES -> page = SettingsPage.ABOUT
            else -> page = SettingsPage.ROOT
        }
    }
    BackHandler(onBack = navigateBack)

    LaunchedEffect(page, state.settingsSyncEnabled) {
        if (page == SettingsPage.BACKUP_SYNC && state.settingsSyncEnabled) {
            controller.loadSettingsSyncHistory()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { LocalizedText(page.title(strings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (page != SettingsPage.ROOT) {
                        IconButton(onClick = navigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedString("Назад"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (page) {
            SettingsPage.ROOT -> SettingsHomePage(
                state = state,
                pushState = pushState,
                strings = strings,
                modifier = Modifier.padding(padding),
                onOpen = {
                    hideKeyboard()
                    page = it
                },
                onLogout = {
                    if (state.isAuthenticated) {
                        onBack()
                        controller.logout()
                    } else {
                        page = SettingsPage.ACCOUNT
                    }
                },
            )

            else -> LazyColumn(
                state = settingsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
                    .testTag(SETTINGS_PAGE_LIST_TEST_TAG),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp),
            ) {
                when (page) {
                    SettingsPage.ACCOUNT -> item {
                        SettingsSection("Twitch") {
                            val profile = state.session?.userId?.let(state.userProfilesById::get)
                            if (state.isAuthenticated) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!profile?.profileImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = profile.profileImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(MaterialTheme.shapes.extraLarge),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        VerbatimText(
                                            profile?.displayName ?: state.session?.login.orEmpty(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        state.session?.login?.let {
                                            VerbatimText("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        LocalizedText(
                                            "Каналов: ${state.channels.size} · модераторских: ${state.moderatedChannelIds.count { id -> state.channels.any { it.id == id } }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                LocalizedText(
                                    "Авторизация сохранена. Если сервис входа временно недоступен, приложение продолжит работать напрямую с Twitch, пока активна текущая сессия. При выходе каналы и локальная история не удаляются.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!state.isAuthorizing) {
                                    OutlinedButton(
                                        onClick = controller::startServerAuthorization,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        LocalizedText("Повторно авторизовать Twitch")
                                    }
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.height(8.dp))
                                            LocalizedText("Ожидаем повторной авторизации в браузере", textAlign = TextAlign.Center)
                                            TextButton(onClick = controller::cancelServerAuthorization) {
                                                LocalizedText("Отменить")
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = controller::logout,
                                    enabled = !state.isAuthorizing && !state.isRevokingDevice && !state.isRevokingAllSessions,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText("Выйти из Twitch")
                                }

                                OutlinedButton(
                                    onClick = { confirmRevokeDevice = true },
                                    enabled = !state.isRevokingDevice && !state.isRevokingAllSessions && !state.isAuthorizing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (state.isRevokingDevice) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText(if (state.isRevokingDevice) "Отзываем устройство…" else "Отозвать это устройство")
                                }
                                LocalizedText(
                                    "Удаляет все серверные сессии, OAuth-переходы и push-данные этой установки. Другие устройства аккаунта не затрагиваются.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                OutlinedButton(
                                    onClick = { confirmRevokeAllSessions = true },
                                    enabled = !state.isRevokingDevice && !state.isRevokingAllSessions && !state.isAuthorizing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (state.isRevokingAllSessions) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText(
                                        if (state.isRevokingAllSessions) {
                                            "Отзываем все сессии…"
                                        } else {
                                            "Отозвать все сессии"
                                        },
                                    )
                                }
                                LocalizedText(
                                    "Завершает вход на всех устройствах Ferventio этого Twitch-аккаунта, удаляет их push-данные и требует повторной авторизации. Локальная история и настройки на устройствах не удаляются.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LocalizedText(
                                    if (state.reauthorizationRequired) "Требуется повторная авторизация" else "Без аккаунта",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                LocalizedText(
                                    if (state.reauthorizationRequired) {
                                        "Серверная или Twitch-сессия завершена. Каналы и локальная история сохранены; повтори вход, чтобы вернуть отправку сообщений и модерацию."
                                    } else {
                                        "Публичные Twitch-чаты доступны только для чтения. Отправка сообщений, платные Twitch-emotes, карточки из Helix и модерация требуют входа."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LocalizedText(
                                    "Откроется безопасная страница входа Ferventio. После подтверждения Twitch приложение автоматически вернётся к чатам.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!state.isAuthorizing) {
                                    Button(
                                        onClick = controller::startServerAuthorization,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        LocalizedText(
                                            if (state.reauthorizationRequired) {
                                                "Повторно авторизовать Twitch"
                                            } else {
                                                "Войти через сайт Ferventio"
                                            },
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.height(8.dp))
                                            LocalizedText("Ожидаем завершения входа в браузере", textAlign = TextAlign.Center)
                                            TextButton(onClick = controller::cancelServerAuthorization) {
                                                LocalizedText("Отменить вход")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.APPEARANCE -> {
                        item {
                            SettingsSection("Тема и масштаб") {
                                LocalizedText("Тема", fontWeight = FontWeight.SemiBold)
                                ChoiceButtons(
                                    values = AppThemeMode.entries,
                                    selected = state.themeMode,
                                    label = { mode ->
                                        when (mode) {
                                            AppThemeMode.LIGHT -> "Светлая"
                                            AppThemeMode.DARK -> "Тёмная"
                                            AppThemeMode.AMOLED -> "AMOLED"
                                        }
                                    },
                                    onSelect = controller::setThemeMode,
                                )
                                LocalizedText("Размер шрифта", fontWeight = FontWeight.SemiBold)
                                ChoiceButtons(
                                    values = listOf(85, 100, 115, 130),
                                    selected = state.fontScalePercent,
                                    label = { "$it%" },
                                    onSelect = controller::setFontScalePercent,
                                )
                                if (state.fontScalePercent !in listOf(85, 100, 115, 130)) {
                                    LocalizedText(
                                        "Текущий масштаб: ${state.fontScalePercent}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item {
                            SettingsSection("Сообщения") {
                                LocalizedText("Плотность сообщений", fontWeight = FontWeight.SemiBold)
                                ChoiceButtons(
                                    values = MessageDensity.entries,
                                    selected = state.messageDensity,
                                    label = { density ->
                                        when (density) {
                                            MessageDensity.COMPACT -> "Плотно"
                                            MessageDensity.NORMAL -> "Обычно"
                                            MessageDensity.RELAXED -> "Свободно"
                                        }
                                    },
                                    onSelect = controller::setMessageDensity,
                                )
                                LocalizedText("Стиль имени", fontWeight = FontWeight.SemiBold)
                                ChoiceButtons(
                                    values = ChatNameStyle.entries,
                                    selected = state.chatNameStyle,
                                    label = { style ->
                                        when (style) {
                                            ChatNameStyle.DISPLAY_NAME -> "Имя"
                                            ChatNameStyle.LOGIN -> "Логин"
                                            ChatNameStyle.DISPLAY_AND_LOGIN -> "Оба"
                                        }
                                    },
                                    onSelect = controller::setChatNameStyle,
                                )
                                SettingsSwitchRow(
                                    title = "Переносить длинные сообщения",
                                    description = "Если выключено, сообщение остаётся в одну строку и сокращается многоточием.",
                                    checked = state.wrapMessageLines,
                                    onCheckedChange = controller::setWrapMessageLines,
                                )
                                SettingsSwitchRow(
                                    title = resourceStrings.string(R.string.ferventio_auto_scroll_new_messages),
                                    description = resourceStrings.string(R.string.ferventio_auto_scroll_new_messages_summary),
                                    checked = state.autoScrollEnabled,
                                    onCheckedChange = controller::setAutoScrollEnabled,
                                )
                                LocalizedText("Цвет упоминаний", fontWeight = FontWeight.SemiBold)
                                MentionColorPicker(
                                    selectedArgb = state.mentionColorArgb,
                                    onSelect = controller::setMentionColorArgb,
                                )
                                SettingsSwitchRow(
                                    title = "Аватары в чате",
                                    description = "Маленький Twitch-аватар на одной линии с badges и ником.",
                                    checked = state.showAvatars,
                                    onCheckedChange = controller::setShowAvatars,
                                )
                                SettingsSwitchRow(
                                    title = "Значки в чате и профиле",
                                    description = "Показывать Twitch и FrankerFaceZ badges рядом с ником и в карточке пользователя.",
                                    checked = state.showBadges,
                                    onCheckedChange = controller::setShowBadges,
                                )
                                SettingsSwitchRow(
                                    title = "Время сообщений",
                                    description = "Показывать локальное время отправки слева.",
                                    checked = state.showTimestamps,
                                    onCheckedChange = controller::setShowTimestamps,
                                )
                                SettingsSwitchRow(
                                    title = "Текст удалённых сообщений",
                                    description = "Показывать исходный текст полупрозрачным вместо [сообщение удалено].",
                                    checked = state.showDeletedMessageContent,
                                    onCheckedChange = controller::setShowDeletedMessageContent,
                                )
                                SettingsSwitchRow(
                                    title = "Системные сообщения в чате",
                                    description = "Серые строки о банах, таймаутах, удалении сообщений, очистке чата и действиях модераторов.",
                                    checked = state.showSystemMessages,
                                    onCheckedChange = controller::setShowSystemMessages,
                                )
                            }
                        }
                        item {
                            SettingsSection("Emotes") {
                                SettingsSwitchRow(
                                    title = "BetterTTV emotes",
                                    description = "Загружать глобальные, канальные и shared BetterTTV emotes.",
                                    checked = state.betterTtvEnabled,
                                    onCheckedChange = controller::setBetterTtvEnabled,
                                )
                                SettingsSwitchRow(
                                    title = "FrankerFaceZ emotes",
                                    description = "Загружать глобальные и канальные FrankerFaceZ emotes.",
                                    checked = state.frankerFaceZEnabled,
                                    onCheckedChange = controller::setFrankerFaceZEnabled,
                                )
                                SettingsSwitchRow(
                                    title = "7TV emotes",
                                    description = "Загружать глобальный и канальный набор 7TV.",
                                    checked = state.sevenTvEnabled,
                                    onCheckedChange = controller::setSevenTvEnabled,
                                )
                                SettingsSwitchRow(
                                    title = "Анимировать emotes",
                                    description = "Показывать animated-версии Twitch, BetterTTV, FFZ и 7TV emotes.",
                                    checked = state.animateEmotes,
                                    onCheckedChange = controller::setAnimateEmotes,
                                )
                                LocalizedText("Размер emotes", fontWeight = FontWeight.SemiBold)
                                ChoiceButtons(
                                    values = listOf(90, 100, 125, 150),
                                    selected = state.emoteScalePercent,
                                    label = { "$it%" },
                                    onSelect = controller::setEmoteScalePercent,
                                )
                            }
                        }
                    }

                    SettingsPage.CHAT -> {
                        item {
                            SettingsSection("Поле ввода") {
                                SettingsSwitchRow(
                                    title = "Enter отправляет сообщение",
                                    description = if (state.sendOnEnter) {
                                        "Enter — отправить, Shift+Enter — новая строка."
                                    } else {
                                        "Enter добавляет новую строку; сообщение отправляется кнопкой."
                                    },
                                    checked = state.sendOnEnter,
                                    onCheckedChange = controller::setSendOnEnter,
                                )
                                SettingsSwitchRow(
                                    title = "Emotes изображениями в поле ввода",
                                    description = if (state.showComposerEmoteImages) {
                                        "Коды emotes визуально заменяются изображениями; в Twitch отправляется обычный текст кода."
                                    } else {
                                        "В поле ввода всегда показываются текстовые коды emotes."
                                    },
                                    checked = state.showComposerEmoteImages,
                                    onCheckedChange = controller::setShowComposerEmoteImages,
                                )
                                LocalizedText(
                                    "Черновик и история отправленных сообщений сохраняются отдельно для каждого канала. На аппаратной клавиатуре ↑/↓ открывают историю, а Tab выбирает подсказку.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SettingsPage.USER_CARD -> {
                        item {
                            SettingsSection("Порядок кнопок модерации") {
                                LocalizedText(
                                    "Кнопки в карточке пользователя отображаются в этом порядке. Перемещай их стрелками.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val allowed = remember(
                                    state.userCardTimeoutPresetsSeconds,
                                    state.userCardShowBanAction,
                                ) {
                                    buildList {
                                        state.userCardTimeoutPresetsSeconds.forEach { add("timeout:$it") }
                                        add("warn")
                                        if (state.userCardShowBanAction) add("ban")
                                        add("unban")
                                    }
                                }
                                val ordered = remember(state.userCardModerationActionOrder, allowed) {
                                    (state.userCardModerationActionOrder.filter { it in allowed } + allowed).distinct()
                                }
                                ordered.forEachIndexed { index, actionId ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (actionId.startsWith("timeout:")) Icons.Default.Timer
                                            else if (actionId == "unban") Icons.Default.Restore
                                            else if (actionId == "warn") Icons.Default.ErrorOutline
                                            else Icons.Default.Block,
                                            contentDescription = null,
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        LocalizedText(
                                            moderationActionLabel(actionId, strings),
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        IconButton(
                                            onClick = { controller.moveUserCardModerationAction(actionId, -1) },
                                            enabled = index > 0,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = localizedString("Поднять"))
                                        }
                                        IconButton(
                                            onClick = { controller.moveUserCardModerationAction(actionId, 1) },
                                            enabled = index < ordered.lastIndex,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = localizedString("Опустить"))
                                        }
                                    }
                                }
                                SettingsSwitchRow(
                                    title = "Показывать Ban",
                                    description = "Постоянная блокировка участвует в настраиваемом порядке.",
                                    checked = state.userCardShowBanAction,
                                    onCheckedChange = controller::setUserCardShowBanAction,
                                )
                            }
                        }
                        item {
                            SettingsSection("Timeout-интервалы") {
                                LocalizedText(
                                    "Интервалы можно задавать как 10s, 5m, 2h или 1d. Новый интервал добавляется в конец списка кнопок.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.userCardTimeoutPresetsSeconds.forEach { seconds ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null)
                                        Spacer(Modifier.width(9.dp))
                                        LocalizedText(
                                            formatLocalizedDuration(seconds, strings),
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        IconButton(
                                            onClick = { controller.removeUserCardTimeoutPreset(seconds) },
                                            enabled = state.userCardTimeoutPresetsSeconds.size > 1,
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = formatLocalizedString(strings.removeTimeoutPresetFormat, formatLocalizedDuration(seconds, strings)),
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = userCardTimeoutInput,
                                    onValueChange = { userCardTimeoutInput = it.take(12) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { LocalizedText("Новый интервал") },
                                    placeholder = { LocalizedText("например, 30s или 6h") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (controller.addUserCardTimeoutPreset(userCardTimeoutInput)) {
                                                userCardTimeoutInput = ""
                                                hideKeyboard()
                                            }
                                        },
                                    ),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (controller.addUserCardTimeoutPreset(userCardTimeoutInput)) {
                                                userCardTimeoutInput = ""
                                                hideKeyboard()
                                            }
                                        },
                                        enabled = userCardTimeoutInput.isNotBlank(),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("Добавить")
                                    }
                                    TextButton(onClick = controller::resetUserCardTimeoutPresets) {
                                        Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("По умолчанию")
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.HISTORY -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsSection(resourceStrings.string(R.string.ferventio_recent_messages_section)) {
                                SettingsSwitchRow(
                                    title = resourceStrings.string(R.string.ferventio_recent_messages_enabled),
                                    description = resourceStrings.string(R.string.ferventio_recent_messages_enabled_summary),
                                    checked = state.recentMessagesEnabled,
                                    onCheckedChange = controller::setRecentMessagesEnabled,
                                )
                                LocalizedText(
                                    resourceStrings.string(R.string.ferventio_recent_messages_privacy_note),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { uriHandler.openUri("https://recent-messages.robotty.de/") },
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText(resourceStrings.string(R.string.ferventio_recent_messages_service_info))
                                }
                            }
                            SettingsSection(resourceStrings.string(R.string.ferventio_local_history_section)) {
                                SettingsSwitchRow(
                                    title = resourceStrings.string(R.string.ferventio_local_history_save_messages),
                                    description = resourceStrings.string(R.string.ferventio_local_history_device_only),
                                    checked = state.localHistoryEnabled,
                                    onCheckedChange = controller::setLocalHistoryEnabled,
                                )
                                if (state.isHistoryLoading) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    LocalizedText(resourceStrings.string(R.string.ferventio_local_history_restoring), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    LocalizedText(
                                        resourceStrings.string(R.string.ferventio_local_history_restored_count, state.restoredHistoryMessageCount),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                state.historyErrorMessage?.let { error ->
                                    LocalizedText(error, color = MaterialTheme.colorScheme.error)
                                }
                                if (state.localHistoryEnabled) {
                                    LocalizedText(resourceStrings.string(R.string.ferventio_local_history_messages_per_channel), fontWeight = FontWeight.SemiBold)
                                    ChoiceButtons(
                                        values = listOf(250, 500, 1_000),
                                        selected = state.localHistoryLimit,
                                        label = Int::toString,
                                        onSelect = controller::setLocalHistoryLimit,
                                    )
                                    LocalizedText(resourceStrings.string(R.string.ferventio_local_history_retention), fontWeight = FontWeight.SemiBold)
                                    ChoiceButtons(
                                        values = listOf(1, 7, 30, 0),
                                        selected = state.localHistoryRetentionDays,
                                        label = { days ->
                                            if (days == 0) {
                                                resourceStrings.string(R.string.ferventio_local_history_unlimited)
                                            } else {
                                                resourceStrings.quantity(
                                                    R.plurals.ferventio_local_history_retention_days,
                                                    days,
                                                    days,
                                                )
                                            }
                                        },
                                        onSelect = controller::setLocalHistoryRetentionDays,
                                    )
                                    LocalizedText(resourceStrings.string(R.string.ferventio_local_history_max_database_size), fontWeight = FontWeight.SemiBold)
                                    ChoiceButtons(
                                        values = listOf(50, 100, 250, 0),
                                        selected = state.localHistoryMaxSizeMb,
                                        label = { sizeMb ->
                                            if (sizeMb == 0) {
                                                resourceStrings.string(R.string.ferventio_local_history_unlimited)
                                            } else {
                                                resourceStrings.string(
                                                    R.string.ferventio_local_history_size_megabytes,
                                                    sizeMb,
                                                )
                                            }
                                        },
                                        onSelect = controller::setLocalHistoryMaxSizeMb,
                                    )
                                }
                                TextButton(onClick = { confirmClearHistory = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText(resourceStrings.string(R.string.ferventio_local_history_clear))
                                }
                            }

                        }
                    }

                    SettingsPage.IMAGE_CACHE -> item {
                        SettingsSection("Coil image cache") {
                            LocalizedText(
                                "Очищает memory cache и disk cache изображений emotes, badges и аватаров.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.isImageCacheClearing) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            state.imageCacheStatusMessage?.let { status ->
                                LocalizedText(status, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = controller::clearImageCache,
                                enabled = !state.isImageCacheClearing,
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                LocalizedText("Очистить image cache")
                            }
                        }
                    }

                    SettingsPage.BACKUP_SYNC -> {
                        item {
                            SettingsSection("Экспорт и импорт") {
                                LocalizedText(
                                    "Резервная копия содержит настройки интерфейса, каналы, workspaces, filters, highlights, ignore rules, команды и избранные emotes. Локальная история сообщений и Twitch-токены не экспортируются.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = onExportSettings,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("Экспорт")
                                    }
                                    OutlinedButton(
                                        onClick = onImportSettings,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("Импорт")
                                    }
                                }
                                state.backupStatusMessage?.takeIf(String::isNotBlank)?.let { status ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            LocalizedText(
                                                status,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            IconButton(onClick = controller::clearBackupStatus) {
                                                Icon(Icons.Default.Close, contentDescription = localizedString("Скрыть"))
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = controller::restorePreImportBackup,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Spacer(Modifier.width(5.dp))
                                    LocalizedText("Восстановить состояние до последнего импорта")
                                }
                                LocalizedText(
                                    "Перед каждым импортом Ferventio автоматически сохраняет текущее состояние. Формат файла имеет версию и SHA-256 содержимого; повреждённый или неподдерживаемый файл не применяется.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            SettingsSection("Синхронизация между устройствами") {
                                SettingsSwitchRow(
                                    title = "Серверная синхронизация",
                                    description = "Хранить версионированную копию настроек в своём Ferventio Server и переносить её между авторизованными устройствами.",
                                    checked = state.settingsSyncEnabled,
                                    onCheckedChange = controller::setSettingsSyncEnabled,
                                )
                                if (!state.isAuthenticated) {
                                    LocalizedText(
                                        "Для синхронизации нужно войти в Twitch через Ferventio Server.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                LocalizedText(
                                    "Статус: ${settingsSyncStatusLabel(state.settingsSyncStatus)}",
                                    color = settingsSyncStatusColor(state.settingsSyncStatus),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LocalizedText(
                                    "Текущая серверная ревизия: ${state.settingsSyncRevision}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.settingsSyncLastSyncedAtMillis > 0L) {
                                    LocalizedText(
                                        "Последняя синхронизация: ${formatPushTime(state.settingsSyncLastSyncedAtMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                state.settingsSyncErrorMessage?.let { error ->
                                    LocalizedText(error, color = MaterialTheme.colorScheme.error)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = controller::synchronizeSettings,
                                        enabled = state.settingsSyncEnabled && state.settingsSyncStatus != SettingsSyncStatus.SYNCING,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (state.settingsSyncStatus == SettingsSyncStatus.SYNCING) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        }
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("Синхронизировать")
                                    }
                                    OutlinedButton(
                                        onClick = controller::loadSettingsSyncHistory,
                                        enabled = state.settingsSyncEnabled,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null)
                                        Spacer(Modifier.width(5.dp))
                                        LocalizedText("История")
                                    }
                                }
                                state.settingsSyncConflict?.let { conflict ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            LocalizedText(
                                                "Конфликт ревизий",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                            LocalizedText(
                                                "Серверная ревизия ${conflict.serverRevision}, обновлена ${formatSyncTimestamp(conflict.serverUpdatedAt)}. Изменения есть и на этом устройстве, поэтому Ferventio не перезаписывает их автоматически.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = controller::useServerSettings,
                                                    modifier = Modifier.weight(1f),
                                                ) { LocalizedText("Использовать сервер") }
                                                Button(
                                                    onClick = controller::overwriteServerSettings,
                                                    modifier = Modifier.weight(1f),
                                                ) { LocalizedText("Заменить сервер") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.settingsSyncHistory.isNotEmpty()) {
                            item {
                                SettingsSection("История изменений") {
                                    state.settingsSyncHistory.forEach { entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                LocalizedText("Ревизия ${entry.revision}", fontWeight = FontWeight.SemiBold)
                                                val revisionDetails = listOfNotNull(
                                                    formatSyncTimestamp(entry.updatedAt),
                                                    entry.appVersion
                                                        ?.takeIf(String::isNotBlank)
                                                        ?.let { "Ferventio $it" },
                                                    localizedString(
                                                        "устройство ${entry.updatedByInstallationId.take(8)}",
                                                    ),
                                                ).joinToString(" · ")
                                                VerbatimText(
                                                    revisionDetails,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { controller.restoreSettingsSyncRevision(entry.revision) },
                                                enabled = entry.revision != state.settingsSyncRevision &&
                                                    state.settingsSyncStatus != SettingsSyncStatus.SYNCING,
                                            ) { LocalizedText("Восстановить") }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.HIGHLIGHTS -> item {
                        SettingsSection("Highlights") {
                            HighlightRulesSettings(state = state, controller = controller)
                        }
                    }

                    SettingsPage.IGNORE -> item {
                        SettingsSection("Ignore") {
                            IgnoreRulesSettings(state = state, controller = controller)
                        }
                    }

                    SettingsPage.FILTERS -> item {
                        SettingsSection("Язык фильтров") {
                            MessageFilterSettings(state = state, controller = controller)
                        }
                    }

                    SettingsPage.NOTIFICATIONS -> item {
                        SettingsSection("Push-уведомления") {
                            SettingsSwitchRow(
                                title = "Уведомлять об ответах",
                                description = "Показывать уведомление, когда входящее сообщение является reply на ваше сообщение.",
                                checked = state.replyNotificationsEnabled,
                                onCheckedChange = controller::setReplyNotificationsEnabled,
                            )
                            LocalizedText(pushStatusLabel(pushState), color = pushStatusColor(pushState.status))
                            LocalizedText(
                                "Уведомления подключаются автоматически после входа в Twitch. Вводить адрес сервера или отдельно включать push не нужно.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                        ).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                LocalizedText("Настройки уведомлений Android")
                            }
                            if (pushState.foregroundServiceRequired) {
                                LocalizedText(
                                    "FOSS-сборка получает уведомления самостоятельно через постоянное защищённое соединение. Android будет показывать служебное уведомление, пока автономный push включён.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                pushState.lastConnectedAtMillis?.let { timestamp ->
                                    LocalizedText("Последнее подключение: ${formatPushTime(timestamp)}")
                                }
                                pushState.lastHeartbeatAtMillis?.let { timestamp ->
                                    LocalizedText("Последний heartbeat: ${formatPushTime(timestamp)}")
                                }
                                if (pushState.reconnectAttempt > 0) {
                                    LocalizedText("Попытка переподключения: ${pushState.reconnectAttempt}")
                                }
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { LocalizedText("Настройки батареи") }
                            }
                            if (pushState.enabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(onClick = onReconnectPush, modifier = Modifier.weight(1f)) {
                                        LocalizedText("Переподключить")
                                    }
                                    OutlinedButton(onClick = onTestPush, modifier = Modifier.weight(1f)) {
                                        LocalizedText("Отправить тест")
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.ADVANCED -> {
                        if (state.isAuthenticated) {
                            item {
                                SettingsSection("EventSub") {
                                    LocalizedText(
                                        "Состояние: ${connectionLabel(state.connectionStatus)}",
                                        color = connectionColor(state.connectionStatus),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    state.connectionDetail?.let { LocalizedText(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    LocalizedText(
                                        "Системные события: ${state.eventSubNoticeChannelIds.size}/${state.channels.size} каналов",
                                        color = if (state.eventSubNoticeChannelIds.size == state.channels.size) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                    if (state.eventSubNoticeFailures.isNotEmpty()) {
                                        VerbatimText(
                                            state.eventSubNoticeFailures.entries.joinToString("\n") { (channel, error) ->
                                                "#$channel: ${resourceStrings.legacy(error)}"
                                            },
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    state.lastConnectionError?.let { LocalizedText(it, color = MaterialTheme.colorScheme.error) }
                                    TextButton(onClick = controller::reconnectEventSub) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        LocalizedText("Переподключить")
                                    }
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(controller.buildEventSubDiagnosticReport()))
                                        uiScope.launch { snackbarHostState.showSnackbar(diagnosticsCopiedText) }
                                    }) { LocalizedText("Копировать диагностику") }
                                }
                            }
                        }
                        item {
                            SettingsSection("Хранилище") {
                                VerbatimText("Room schema: 9", fontWeight = FontWeight.SemiBold)
                                LocalizedText(
                                    "Активные миграции: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. История, Mentions и позиция прокрутки сохраняются без destructive migration.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SettingsPage.ABOUT -> {
                        val aboutLinks = configuredAboutLinks(strings)
                        item {
                            SettingsSection(strings.aboutProjectTitle) {
                                LocalizedText("Версия ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                                LocalizedText(
                                    strings.aboutSummary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LocalizedText(
                                    strings.aboutProjectDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (aboutLinks.isNotEmpty()) {
                            item {
                                SettingsSection(strings.aboutLinksTitle) {
                                    aboutLinks.forEachIndexed { index, link ->
                                        SettingsMenuRow(
                                            icon = link.icon,
                                            title = link.title,
                                            summary = link.summary,
                                            onClick = { uriHandler.openUri(link.url) },
                                        )
                                        if (index < aboutLinks.lastIndex) {
                                            SettingsGroupDivider()
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSection(strings.legalInformation) {
                                if (BuildConfig.SHOW_PRIVACY_POLICY_IN_APP) {
                                    OutlinedButton(
                                        onClick = { page = SettingsPage.PRIVACY },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        LocalizedText(strings.privacyPolicy)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { page = SettingsPage.LICENSES },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    LocalizedText(strings.openSourceLicenses)
                                }
                                if (
                                    BuildConfig.SHOW_PRIVACY_POLICY_IN_APP &&
                                    BuildConfig.PRIVACY_POLICY_URL.isNotBlank()
                                ) {
                                    TextButton(
                                        onClick = { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        LocalizedText(strings.openPublishedWebVersion)
                                    }
                                }
                            }
                        }
                        if (BuildConfig.LOCAL_CRASH_REPORTING) {
                            item {
                                SettingsSection(strings.localCrashReports) {
                                    LocalizedText(
                                        strings.localCrashReportsDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = onExportCrashReports,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        LocalizedText(strings.exportReports)
                                    }
                                    TextButton(
                                        onClick = { confirmClearCrashReports = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        LocalizedText(strings.deleteLocalReports)
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.LANGUAGE -> {
                        val normalizedQuery = languageQuery.trim().lowercase()
                        val visibleLanguages = AppLanguage.entries.filter { language ->
                            normalizedQuery.isEmpty() || languageSearchText(language, strings)
                                .lowercase()
                                .contains(normalizedQuery)
                        }
                        item(key = "language-list") {
                            SettingsSection(strings.languagePageTitle) {
                                OutlinedTextField(
                                    value = languageQuery,
                                    onValueChange = { languageQuery = it.take(80) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { LocalizedText(strings.languageSearchHint) },
                                )
                                if (visibleLanguages.isEmpty()) {
                                    LocalizedText(
                                        strings.languageNoResults,
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Column {
                                        visibleLanguages.forEachIndexed { index, language ->
                                            LanguageSelectionRow(
                                                language = language,
                                                strings = strings,
                                                selected = language == state.appLanguage,
                                                onClick = { controller.setAppLanguage(language) },
                                            )
                                            if (index < visibleLanguages.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 62.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.PRIVACY -> {
                        item {
                            SettingsSection(strings.privacyTitle) {
                                LocalizedText("Действует с $PRIVACY_POLICY_EFFECTIVE_DATE", fontWeight = FontWeight.Bold)
                                LocalizedText(
                                    "Текст встроен в приложение и доступен офлайн. Параметры оператора и контакта задаются для конкретной release-сборки.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            FerventioLegalContent.privacySections(
                                localCrashReporting = BuildConfig.LOCAL_CRASH_REPORTING,
                                pushTransport = BuildConfig.PUSH_TRANSPORT,
                                operatorName = BuildConfig.PRIVACY_OPERATOR_NAME,
                                privacyContact = BuildConfig.PRIVACY_CONTACT,
                            ),
                            key = PrivacyPolicySection::id,
                        ) { section ->
                            PrivacyPolicyCard(section)
                        }
                    }

                    SettingsPage.LICENSES -> {
                        item {
                            SettingsSection(strings.licensesTitle) {
                                LocalizedText(
                                    "Ferventio включает перечисленные runtime-компоненты. Test-only инструменты в список не входят. Полные license texts доступны ниже без сети.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            FerventioLegalContent.openSourceNotices(
                                includePlayLibraries = BuildConfig.PUSH_TRANSPORT == "fcm",
                            ),
                            key = OpenSourceNotice::id,
                        ) { notice ->
                            OpenSourceNoticeCard(notice, uriHandler::openUri)
                        }
                        items(FerventioLegalContent.licenseTexts, key = LicenseText::id) { license ->
                            LicenseTextCard(license)
                        }
                    }

                    SettingsPage.ROOT -> Unit
                }
            }
        }
    }

    if (confirmRevokeDevice) {
        AlertDialog(
            onDismissRequest = { if (!state.isRevokingDevice) confirmRevokeDevice = false },
            title = { LocalizedText("Отозвать это устройство?") },
            text = {
                LocalizedText(
                    "Сервер удалит все сессии и push-данные этой установки. Локальные каналы, история и настройки останутся на устройстве.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isRevokingDevice,
                    onClick = {
                        confirmRevokeDevice = false
                        controller.revokeDevice()
                    },
                ) { LocalizedText("Отозвать") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isRevokingDevice,
                    onClick = { confirmRevokeDevice = false },
                ) { LocalizedText("Отмена") }
            },
        )
    }

    if (confirmRevokeAllSessions) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isRevokingAllSessions) confirmRevokeAllSessions = false
            },
            title = { LocalizedText("Отозвать все сессии аккаунта?") },
            text = {
                LocalizedText(
                    "Все устройства Ferventio этого Twitch-аккаунта потеряют серверную авторизацию и push-доступ. На каждом устройстве потребуется войти снова. Локальные каналы, история, drafts и настройки сохранятся.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isRevokingAllSessions,
                    onClick = {
                        confirmRevokeAllSessions = false
                        controller.revokeAllSessions()
                    },
                ) { LocalizedText("Отозвать все") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isRevokingAllSessions,
                    onClick = { confirmRevokeAllSessions = false },
                ) { LocalizedText("Отмена") }
            },
        )
    }

    if (confirmClearCrashReports) {
        AlertDialog(
            onDismissRequest = { confirmClearCrashReports = false },
            title = { LocalizedText("Удалить локальные отчёты?") },
            text = {
                LocalizedText(
                    "Все сохранённые FOSS crash reports будут удалены с устройства. Настройки, каналы и история чата не изменятся.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCrashReports = false
                    onClearCrashReports()
                }) { LocalizedText("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCrashReports = false }) { LocalizedText("Отмена") }
            },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { LocalizedText("Очистить историю?") },
            text = { LocalizedText("Все сохранённые сообщения и позиции прокрутки будут удалены с устройства.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    controller.clearLocalHistory()
                }) { LocalizedText("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { LocalizedText("Отмена") } },
        )
    }
}
