package io.ferventio.shared.ui.app

import kotlin.time.Clock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.NotificationEventType
import io.ferventio.app.domain.NotificationMuteRemainingUnit
import io.ferventio.app.domain.notificationMuteRemaining
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.push.PushAuthorizationStatus
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.AppearanceSettingsPresets
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedSettingsSaveStatus
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private enum class SharedSettingsPage {
    ROOT,
    APPEARANCE,
    CHAT,
    USER_CARD,
    NOTIFICATIONS,
    HIGHLIGHTS,
    IGNORE,
    FILTERS,
    HISTORY,
    IMAGE_CACHE,
    BACKUP_SYNC,
    ACCOUNT,
    LANGUAGE,
    ABOUT,
    PRIVACY,
    LICENSES,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioSettingsSheet(
    state: SharedAppSettingsStateHolder,
    notificationAuthorizationStatus: PushAuthorizationStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSave: (SharedAppPreferences) -> Unit,
    onUpsertHighlightRule: (HighlightRule) -> Unit = {},
    onDeleteHighlightRule: (String) -> Unit = {},
    onUpsertIgnoreRule: (IgnoreRule) -> Unit = {},
    onDeleteIgnoreRule: (String) -> Unit = {},
    onUpsertSavedFilter: (SavedMessageFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
    onImportSavedFilters: (String) -> Unit = {},
    canAddSavedFilterToSplit: Boolean = false,
    onAddSavedFilterSplit: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var persistedPreferences by remember { mutableStateOf(state.preferences) }
    val runtime = LocalFerventioRuntimeState.current
    val notificationAction = notificationPermissionAction(notificationAuthorizationStatus)
    val aboutInfo = LocalFerventioAboutInfo.current
    val accountActions = LocalFerventioAccountActions.current
    val backupActions = LocalFerventioSettingsBackupActions.current
    val privacyPlatformInfo = LocalFerventioPrivacyPlatformInfo.current
    var page by remember { mutableStateOf(SharedSettingsPage.ROOT) }

    fun update(transform: (SharedAppPreferences) -> SharedAppPreferences) {
        state.updateLocally(transform)
    }

    fun persistIfChanged() {
        if (
            shouldPersistSettings(
                current = state.preferences,
                lastRequested = persistedPreferences,
                saveStatus = state.saveStatus,
            )
        ) {
            persistedPreferences = state.preferences
            onSave(state.preferences)
        }
    }

    fun saveAndDismiss() {
        persistIfChanged()
        onDismiss()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = settingsPageTitle(page),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (page != SharedSettingsPage.ROOT) {
                        IconButton(
                            onClick = {
                                page = when (page) {
                                    SharedSettingsPage.PRIVACY,
                                    SharedSettingsPage.LICENSES,
                                    -> SharedSettingsPage.ABOUT
                                    else -> SharedSettingsPage.ROOT
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.settings_back),
                            )
                        }
                    }
                },
                actions = {
                    if (page == SharedSettingsPage.ROOT) {
                        IconButton(onClick = ::saveAndDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.settings_close),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        val horizontalPadding = if (page == SharedSettingsPage.ROOT) 0.dp else 14.dp
        val topPadding = if (page == SharedSettingsPage.ROOT) 6.dp else 12.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = horizontalPadding,
                    top = topPadding,
                    end = horizontalPadding,
                    bottom = 16.dp,
                ),
        ) {
            when (page) {
                SharedSettingsPage.ROOT -> {
                    if (accountActions.accountManagementAvailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                        ) {
                            FerventioSettingsAccountProfileCard(
                                onOpenAccount = { page = SharedSettingsPage.ACCOUNT },
                                onSignOut = {
                                    saveAndDismiss()
                                    accountActions.onSignOut()
                                },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    SettingsHome(
                        preferences = state.preferences,
                        syncRevision = state.syncRevision,
                        aboutInfo = aboutInfo,
                        onOpen = { page = it },
                    )
                }
                SharedSettingsPage.APPEARANCE -> AppearanceSettingsPage(
                    preferences = state.preferences,
                    update = ::update,
                )
                SharedSettingsPage.CHAT -> ChatBehaviorSettingsPage(
                    preferences = state.preferences,
                    update = ::update,
                )
                SharedSettingsPage.USER_CARD -> UserCardSettingsPage(
                    preferences = state.preferences,
                    update = ::update,
                )
                SharedSettingsPage.NOTIFICATIONS -> NotificationsSettingsPage(
                    preferences = state.preferences,
                    channels = runtime.workspace.channels,
                    notificationAction = notificationAction,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    update = ::update,
                )
                SharedSettingsPage.HIGHLIGHTS -> FerventioSettingsSection(
                    title = stringResource(Res.string.message_rules_highlights),
                ) {
                    FerventioMessageRulesPage(
                        state = runtime.messageRules,
                        section = FerventioMessageRulesSection.HIGHLIGHTS,
                        onUpsertHighlightRule = onUpsertHighlightRule,
                        onDeleteHighlightRule = onDeleteHighlightRule,
                        onUpsertIgnoreRule = onUpsertIgnoreRule,
                        onDeleteIgnoreRule = onDeleteIgnoreRule,
                    )
                }
                SharedSettingsPage.IGNORE -> FerventioSettingsSection(
                    title = stringResource(Res.string.message_rules_ignore),
                ) {
                    FerventioMessageRulesPage(
                        state = runtime.messageRules,
                        section = FerventioMessageRulesSection.IGNORE,
                        onUpsertHighlightRule = onUpsertHighlightRule,
                        onDeleteHighlightRule = onDeleteHighlightRule,
                        onUpsertIgnoreRule = onUpsertIgnoreRule,
                        onDeleteIgnoreRule = onDeleteIgnoreRule,
                    )
                }
                SharedSettingsPage.FILTERS -> FerventioSettingsSection(
                    title = stringResource(Res.string.settings_filter_language),
                ) {
                    FerventioSavedFiltersPage(
                        state = runtime.savedFilters,
                        onUpsert = onUpsertSavedFilter,
                        onDelete = onDeleteSavedFilter,
                        onImport = onImportSavedFilters,
                        canAddToSplit = canAddSavedFilterToSplit,
                        onAddToSplit = onAddSavedFilterSplit,
                    )
                }
                SharedSettingsPage.HISTORY -> FerventioHistorySettingsPage(
                    preferences = state.preferences,
                    update = ::update,
                )
                SharedSettingsPage.IMAGE_CACHE -> FerventioImageCacheSettingsPage()
                SharedSettingsPage.BACKUP_SYNC -> SettingsBackupPage(
                    syncRevision = state.syncRevision,
                    actions = backupActions,
                    onBeforeExport = ::persistIfChanged,
                )
                SharedSettingsPage.ACCOUNT -> ProvideFerventioAccountActions(
                    actions = FerventioAccountActions(
                        onReauthorize = accountActions.onReauthorize?.let { action ->
                            {
                                persistIfChanged()
                                action()
                            }
                        },
                        onSignOut = {
                            saveAndDismiss()
                            accountActions.onSignOut()
                        },
                        onRevokeDevice = accountActions.onRevokeDevice?.let { action ->
                            {
                                persistIfChanged()
                                action()
                            }
                        },
                        onRevokeAllSessions = accountActions.onRevokeAllSessions?.let { action ->
                            {
                                persistIfChanged()
                                action()
                            }
                        },
                    ),
                ) {
                    FerventioAccountSettingsPage()
                }
                SharedSettingsPage.LANGUAGE -> FerventioLanguageSettingsPage(
                    preferences = state.preferences,
                    onLanguageSelected = { language ->
                        val updated = state.updateLocally { current ->
                            current.copy(appLanguage = language)
                        }
                        if (updated != persistedPreferences) {
                            persistedPreferences = updated
                            onSave(updated)
                        }
                    },
                )
                SharedSettingsPage.ABOUT -> FerventioAboutSettingsPage(
                    info = aboutInfo,
                    onOpenPrivacyPolicy = if (privacyPlatformInfo != null) {
                        { page = SharedSettingsPage.PRIVACY }
                    } else {
                        null
                    },
                    onOpenLicenses = { page = SharedSettingsPage.LICENSES },
                )
                SharedSettingsPage.PRIVACY -> privacyPlatformInfo?.let { platformInfo ->
                    FerventioPrivacySettingsPage(
                        info = aboutInfo,
                        platformInfo = platformInfo,
                    )
                }
                SharedSettingsPage.LICENSES -> FerventioLicensesSettingsPage()
            }

            SettingsSaveState(state)
        }
    }
}

@Composable
private fun settingsPageTitle(page: SharedSettingsPage): String = when (page) {
    SharedSettingsPage.ROOT -> stringResource(Res.string.settings_title)
    SharedSettingsPage.APPEARANCE -> stringResource(Res.string.settings_messages_appearance)
    SharedSettingsPage.CHAT -> stringResource(Res.string.settings_input_behavior)
    SharedSettingsPage.USER_CARD -> stringResource(Res.string.settings_user_card)
    SharedSettingsPage.NOTIFICATIONS -> stringResource(Res.string.notifications_title)
    SharedSettingsPage.HIGHLIGHTS -> stringResource(Res.string.message_rules_highlights)
    SharedSettingsPage.IGNORE -> stringResource(Res.string.message_rules_ignore)
    SharedSettingsPage.FILTERS -> stringResource(Res.string.settings_filter_language)
    SharedSettingsPage.HISTORY -> stringResource(Res.string.settings_history)
    SharedSettingsPage.IMAGE_CACHE -> stringResource(Res.string.image_cache_title)
    SharedSettingsPage.BACKUP_SYNC -> stringResource(Res.string.settings_export_sync)
    SharedSettingsPage.ACCOUNT -> stringResource(Res.string.settings_account)
    SharedSettingsPage.LANGUAGE -> stringResource(Res.string.settings_language)
    SharedSettingsPage.ABOUT -> stringResource(Res.string.settings_about)
    SharedSettingsPage.PRIVACY -> stringResource(Res.string.about_privacy_policy)
    SharedSettingsPage.LICENSES -> stringResource(Res.string.about_open_source_licenses)
}

@Composable
private fun SettingsHome(
    preferences: SharedAppPreferences,
    syncRevision: Long,
    aboutInfo: FerventioAboutInfo,
    onOpen: (SharedSettingsPage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsHomeGroup(stringResource(Res.string.settings_home_chat_group)) {
            SettingsMenuRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(Res.string.settings_messages_appearance),
                summary = stringResource(Res.string.settings_messages_appearance_summary),
                onClick = { onOpen(SharedSettingsPage.APPEARANCE) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.Tune,
                title = stringResource(Res.string.settings_input_behavior),
                summary = stringResource(Res.string.settings_input_behavior_summary),
                onClick = { onOpen(SharedSettingsPage.CHAT) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.Person,
                title = stringResource(Res.string.settings_user_card),
                summary = stringResource(Res.string.settings_user_card_summary),
                onClick = { onOpen(SharedSettingsPage.USER_CARD) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_features_group)) {
            SettingsMenuRow(
                icon = Icons.Default.Palette,
                title = stringResource(Res.string.message_rules_highlights),
                summary = stringResource(Res.string.settings_highlights_summary),
                onClick = { onOpen(SharedSettingsPage.HIGHLIGHTS) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.Block,
                title = stringResource(Res.string.message_rules_ignore),
                summary = stringResource(Res.string.settings_ignore_summary),
                onClick = { onOpen(SharedSettingsPage.IGNORE) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.FilterAlt,
                title = stringResource(Res.string.settings_filter_language),
                summary = stringResource(Res.string.settings_filter_language_summary),
                onClick = { onOpen(SharedSettingsPage.FILTERS) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.notifications_title),
                summary = stringResource(Res.string.settings_notifications_summary),
                onClick = { onOpen(SharedSettingsPage.NOTIFICATIONS) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_data_group)) {
            SettingsMenuRow(
                icon = Icons.Default.History,
                title = stringResource(Res.string.settings_history),
                summary = stringResource(Res.string.settings_history_summary),
                onClick = { onOpen(SharedSettingsPage.HISTORY) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.DeleteSweep,
                title = stringResource(Res.string.image_cache_title),
                summary = stringResource(Res.string.image_cache_summary),
                onClick = { onOpen(SharedSettingsPage.IMAGE_CACHE) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.ContentCopy,
                title = stringResource(Res.string.settings_export_sync),
                summary = if (syncRevision > 0L) {
                    stringResource(Res.string.settings_backup_revision, syncRevision)
                } else {
                    stringResource(Res.string.settings_export_sync_summary)
                },
                onClick = { onOpen(SharedSettingsPage.BACKUP_SYNC) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_app_group)) {
            SettingsMenuRow(
                icon = Icons.Default.Translate,
                title = stringResource(Res.string.settings_language),
                summary = sharedLanguageLabel(preferences.appLanguage),
                onClick = { onOpen(SharedSettingsPage.LANGUAGE) },
            )
            SettingsGroupDivider()
            SettingsMenuRow(
                icon = Icons.Default.Info,
                title = stringResource(Res.string.settings_about),
                summary = stringResource(
                    Res.string.settings_about_summary,
                    aboutInfo.versionName.ifBlank { "—" },
                ),
                onClick = { onOpen(SharedSettingsPage.ABOUT) },
            )
        }
    }
}

@Composable
private fun SettingsHomeGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
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
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
            )
            summary?.let { value ->
                Text(
                    text = value,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.4f,
            ),
        )
    }
}

@Composable
private fun SettingsBackupPage(
    syncRevision: Long,
    actions: FerventioSettingsBackupActions,
    onBeforeExport: () -> Unit,
) {
    val historyState = actions.revisionHistoryState
    val backupBusy = when (actions.state.status) {
        SharedSettingsBackupStatus.EXPORTING,
        SharedSettingsBackupStatus.IMPORTING,
        SharedSettingsBackupStatus.RESOLVING,
        SharedSettingsBackupStatus.CONFLICT,
        -> true
        SharedSettingsBackupStatus.IDLE,
        SharedSettingsBackupStatus.SYNCED,
        SharedSettingsBackupStatus.FAILED,
        -> false
    }
    val historyBusy = historyState.status == SharedSettingsRevisionHistoryStatus.LOADING ||
        historyState.status == SharedSettingsRevisionHistoryStatus.RESTORING
    var pendingRestoreRevision by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(actions.revisionHistoryAvailable) {
        if (actions.revisionHistoryAvailable) {
            actions.onRefreshRevisionHistory?.invoke()
        }
    }

    pendingRestoreRevision?.let { revision ->
        AlertDialog(
            onDismissRequest = { pendingRestoreRevision = null },
            title = {
                Text(stringResource(Res.string.settings_revision_history_confirm_title, revision))
            },
            text = { Text(stringResource(Res.string.settings_revision_history_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreRevision = null
                        actions.onRestoreRevision?.invoke(revision)
                    },
                ) {
                    Text(stringResource(Res.string.settings_revision_history_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreRevision = null }) {
                    Text(stringResource(Res.string.settings_revision_history_cancel))
                }
            },
        )
    }

    SettingsSectionTitle(stringResource(Res.string.settings_export_sync))
    Text(
        text = stringResource(Res.string.settings_backup_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = if (syncRevision > 0L) {
            stringResource(Res.string.settings_backup_revision, syncRevision)
        } else {
            stringResource(Res.string.settings_backup_not_synced)
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = {
                onBeforeExport()
                actions.onExport?.invoke()
            },
            enabled = actions.onExport != null && !historyBusy,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(Res.string.settings_backup_export))
        }
        TextButton(
            onClick = { actions.onImport?.invoke() },
            enabled = actions.onImport != null && !historyBusy,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(Res.string.settings_backup_import))
        }
    }
    if (!actions.fileTransferAvailable) {
        Text(
            text = stringResource(Res.string.settings_backup_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (actions.revisionHistoryAvailable) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SettingsSectionTitle(stringResource(Res.string.settings_revision_history_title))
        Text(
            text = stringResource(Res.string.settings_revision_history_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { actions.onRefreshRevisionHistory?.invoke() },
            enabled = !backupBusy && !historyBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.settings_revision_history_refresh))
        }

        when (historyState.status) {
            SharedSettingsRevisionHistoryStatus.LOADING -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(Res.string.settings_revision_history_loading),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SharedSettingsRevisionHistoryStatus.RESTORING -> historyState.restoringRevision?.let { revision ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(Res.string.settings_revision_history_restoring, revision),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SharedSettingsRevisionHistoryStatus.FAILED -> Text(
                text = stringResource(
                    Res.string.settings_revision_history_error,
                    historyState.errorMessage.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SharedSettingsRevisionHistoryStatus.IDLE -> historyState.lastRestoredRevision?.let { revision ->
                Text(
                    text = stringResource(Res.string.settings_revision_history_restored, revision),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        if (historyState.entries.isEmpty() && historyState.status == SharedSettingsRevisionHistoryStatus.IDLE) {
            Text(
                text = stringResource(Res.string.settings_revision_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            historyState.entries.forEach { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        Res.string.settings_revision_history_revision,
                                        entry.revision,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(
                                        Res.string.settings_revision_history_updated,
                                        entry.updatedAt,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                entry.appVersion?.takeIf(String::isNotBlank)?.let { appVersion ->
                                    Text(
                                        text = stringResource(
                                            Res.string.settings_revision_history_app_version,
                                            appVersion,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { pendingRestoreRevision = entry.revision },
                                enabled = !backupBusy && !historyBusy,
                            ) {
                                Text(stringResource(Res.string.settings_revision_history_restore))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FerventioSettingsSection(
            title = stringResource(Res.string.settings_theme_scale_section),
        ) {
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_theme),
                options = listOf(
                    AppThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
                    AppThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
                    AppThemeMode.AMOLED to stringResource(Res.string.settings_theme_amoled),
                ),
                selected = preferences.themeMode,
                onSelected = { value -> update { it.copy(themeMode = value) } },
            )
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_font_size),
                options = AppearanceSettingsPresets.FONT_SCALE_PERCENT.map { value -> value to "$value%" },
                selected = preferences.fontScalePercent,
                onSelected = { value -> update { it.copy(fontScalePercent = value) } },
            )
        }

        FerventioSettingsSection(
            title = stringResource(Res.string.settings_messages_section),
        ) {
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_density),
                options = listOf(
                    MessageDensity.COMPACT to stringResource(Res.string.settings_density_compact),
                    MessageDensity.NORMAL to stringResource(Res.string.settings_density_normal),
                    MessageDensity.RELAXED to stringResource(Res.string.settings_density_relaxed),
                ),
                selected = preferences.messageDensity,
                onSelected = { value -> update { it.copy(messageDensity = value) } },
            )
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_name_style),
                options = listOf(
                    ChatNameStyle.DISPLAY_NAME to stringResource(Res.string.settings_name_display),
                    ChatNameStyle.LOGIN to stringResource(Res.string.settings_name_login),
                    ChatNameStyle.DISPLAY_AND_LOGIN to stringResource(Res.string.settings_name_both),
                ),
                selected = preferences.nameStyle,
                onSelected = { value -> update { it.copy(nameStyle = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_wrap_messages),
                checked = preferences.wrapMessageLines,
                onCheckedChange = { value -> update { it.copy(wrapMessageLines = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_auto_scroll),
                checked = preferences.autoScrollEnabled,
                onCheckedChange = { value -> update { it.copy(autoScrollEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_repeat_collapse),
                checked = preferences.repeatCollapseEnabled,
                onCheckedChange = { value -> update { it.copy(repeatCollapseEnabled = value) } },
            )
            Text(
                text = stringResource(Res.string.settings_mention_color),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            SettingsMentionColorPicker(
                selectedArgb = preferences.mentionColorArgb,
                onSelected = { value -> update { it.copy(mentionColorArgb = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_avatars),
                checked = preferences.showAvatars,
                onCheckedChange = { value -> update { it.copy(showAvatars = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_badges),
                checked = preferences.showBadges,
                onCheckedChange = { value -> update { it.copy(showBadges = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_timestamps),
                checked = preferences.showTimestamps,
                onCheckedChange = { value -> update { it.copy(showTimestamps = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_deleted_content),
                checked = preferences.showDeletedMessageContent,
                onCheckedChange = { value -> update { it.copy(showDeletedMessageContent = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_system_messages),
                checked = preferences.showSystemMessages,
                onCheckedChange = { value -> update { it.copy(showSystemMessages = value) } },
            )
        }

        FerventioSettingsSection(
            title = stringResource(Res.string.settings_emotes_section),
        ) {
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_bttv),
                checked = preferences.betterTtvEnabled,
                onCheckedChange = { value -> update { it.copy(betterTtvEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_ffz),
                checked = preferences.frankerFaceZEnabled,
                onCheckedChange = { value -> update { it.copy(frankerFaceZEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_7tv),
                checked = preferences.sevenTvEnabled,
                onCheckedChange = { value -> update { it.copy(sevenTvEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_animate_emotes),
                checked = preferences.animateEmotes,
                onCheckedChange = { value -> update { it.copy(animateEmotes = value) } },
            )
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_emote_size),
                options = AppearanceSettingsPresets.EMOTE_SCALE_PERCENT.map { value -> value to "$value%" },
                selected = preferences.emoteScalePercent,
                onSelected = { value -> update { it.copy(emoteScalePercent = value) } },
            )
        }
    }
}

@Composable
private fun ChatBehaviorSettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    FerventioSettingsSection(
        title = stringResource(Res.string.settings_composer),
    ) {
        SettingsSwitchRow(
            label = stringResource(Res.string.settings_send_on_enter),
            checked = preferences.sendOnEnter,
            onCheckedChange = { value -> update { it.copy(sendOnEnter = value) } },
        )
        SettingsSwitchRow(
            label = stringResource(Res.string.settings_composer_emote_images),
            checked = preferences.showComposerEmoteImages,
            onCheckedChange = { value -> update { it.copy(showComposerEmoteImages = value) } },
        )
        QuickModerationSettingsSection()
    }
}

@Composable
private fun NotificationsSettingsPage(
    preferences: SharedAppPreferences,
    channels: List<ChatChannel>,
    notificationAction: NotificationPermissionAction,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    var expandedChannelId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preferences.notificationPreferences) {
        val cleaned = preferences.notificationPreferences.clearExpiredChannelMutes(
            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        if (cleaned !== preferences.notificationPreferences) {
            update { current -> current.copy(notificationPreferences = cleaned) }
        }
    }

    fun legacyDefault(ruleId: String): Boolean = when (ruleId) {
        NotificationEventType.REPLY.ruleId -> preferences.replyNotificationsEnabled
        NotificationEventType.AUTOMOD_HOLD.ruleId -> preferences.autoModNotificationsEnabled
        else -> true
    }

    fun updateGlobalEvent(event: NotificationEventType, enabled: Boolean) {
        update { current ->
            val policy = current.notificationPreferences.withGlobalEvent(event.ruleId, enabled)
            when (event) {
                NotificationEventType.REPLY -> current.copy(
                    replyNotificationsEnabled = enabled,
                    notificationPreferences = policy,
                )
                NotificationEventType.AUTOMOD_HOLD -> current.copy(
                    autoModNotificationsEnabled = enabled,
                    notificationPreferences = policy,
                )
                else -> current.copy(notificationPreferences = policy)
            }
        }
    }

    FerventioSettingsSection(
        title = stringResource(Res.string.settings_notifications_section),
    ) {
        SettingsSwitchRow(
            label = stringResource(Res.string.notifications_master),
            checked = preferences.notificationPreferences.enabled,
            onCheckedChange = { enabled ->
                update { current ->
                    current.copy(
                        notificationPreferences =
                            current.notificationPreferences.withEnabled(enabled),
                    )
                }
            },
        )

        Text(
            text = stringResource(Res.string.notifications_global_events),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        NotificationEventType.entries.forEach { event ->
            SettingsSwitchRow(
                label = notificationEventLabel(event),
                checked = preferences.notificationPreferences.isEnabled(
                    ruleId = event.ruleId,
                    legacyDefault = ::legacyDefault,
                ),
                onCheckedChange = { enabled -> updateGlobalEvent(event, enabled) },
            )
        }

        if (channels.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(Res.string.notifications_per_channel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            channels.forEach { channel ->
                val custom = channel.id in preferences.notificationPreferences.channelOverrides
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${channel.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(Res.string.notifications_custom_channel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = custom,
                        onCheckedChange = { enabled ->
                            update { current ->
                                current.copy(
                                    notificationPreferences = if (enabled) {
                                        current.notificationPreferences
                                            .enableChannelOverrides(channel.id)
                                    } else {
                                        current.notificationPreferences
                                            .clearChannelOverride(channel.id)
                                    },
                                )
                            }
                            expandedChannelId = if (enabled) {
                                channel.id
                            } else {
                                expandedChannelId.takeUnless { it == channel.id }
                            }
                        },
                    )
                }

                if (custom) {
                    val expanded = expandedChannelId == channel.id
                    TextButton(
                        onClick = {
                            expandedChannelId = if (expanded) null else channel.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (expanded) {
                                stringResource(Res.string.notifications_hide_channel)
                            } else {
                                stringResource(Res.string.notifications_configure_channel)
                            },
                        )
                    }
                }

                if (custom && expandedChannelId == channel.id) {
                    SettingsSwitchRow(
                        label = stringResource(Res.string.notifications_channel_master),
                        checked = preferences.notificationPreferences.channelOverrides
                            .getValue(channel.id)
                            .enabled,
                        onCheckedChange = { enabled ->
                            update { current ->
                                current.copy(
                                    notificationPreferences =
                                        current.notificationPreferences
                                            .withChannelEnabled(channel.id, enabled),
                                )
                            }
                        },
                    )
                    val channelPreferences = preferences.notificationPreferences
                        .channelOverrides
                        .getValue(channel.id)
                    Text(
                        text = stringResource(Res.string.notifications_mute_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val remainingMute = notificationMuteRemaining(
                        mutedUntilEpochMillis = channelPreferences.mutedUntilEpochMillis,
                        nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    )
                    val muteHint = if (remainingMute != null) {
                        val unit = stringResource(
                            when (remainingMute.unit) {
                                NotificationMuteRemainingUnit.MINUTES ->
                                    Res.string.notifications_mute_unit_minutes
                                NotificationMuteRemainingUnit.HOURS ->
                                    Res.string.notifications_mute_unit_hours
                                NotificationMuteRemainingUnit.DAYS ->
                                    Res.string.notifications_mute_unit_days
                            },
                        )
                        stringResource(
                            Res.string.notifications_mute_remaining,
                            remainingMute.value,
                            unit,
                        )
                    } else {
                        stringResource(Res.string.notifications_mute_hint)
                    }
                    Text(
                        text = muteHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            Res.string.notifications_mute_1h to 60 * 60 * 1_000L,
                            Res.string.notifications_mute_8h to 8 * 60 * 60 * 1_000L,
                            Res.string.notifications_mute_1d to 24 * 60 * 60 * 1_000L,
                        ).forEach { (labelRes, durationMillis) ->
                            TextButton(
                                onClick = {
                                    update { current ->
                                        current.copy(
                                            notificationPreferences =
                                                current.notificationPreferences
                                                    .withChannelMutedUntil(
                                                        channelId = channel.id,
                                                        mutedUntilEpochMillis =
                                                            Clock.System.now()
                                                                .toEpochMilliseconds() +
                                                                durationMillis,
                                                    ),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                    if (channelPreferences.mutedUntilEpochMillis != null) {
                        TextButton(
                            onClick = {
                                update { current ->
                                    current.copy(
                                        notificationPreferences =
                                            current.notificationPreferences
                                                .withChannelMutedUntil(
                                                    channelId = channel.id,
                                                    mutedUntilEpochMillis = null,
                                                ),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.notifications_unmute))
                        }
                    }
                    if (channelPreferences.eventOverrides.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                update { current ->
                                    current.copy(
                                        notificationPreferences =
                                            current.notificationPreferences
                                                .clearChannelEventOverrides(channel.id),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.notifications_use_global_all_events))
                        }
                    }
                    NotificationEventType.entries.forEach { event ->
                        val overridden = event.ruleId in preferences.notificationPreferences
                            .channelOverrides
                            .getValue(channel.id)
                            .eventOverrides
                        SettingsSwitchRow(
                            label = notificationEventLabel(event),
                            checked = preferences.notificationPreferences.isEnabled(
                                ruleId = event.ruleId,
                                channelId = channel.id,
                                legacyDefault = ::legacyDefault,
                            ),
                            onCheckedChange = { enabled ->
                                update { current ->
                                    current.copy(
                                        notificationPreferences =
                                            current.notificationPreferences.withChannelEvent(
                                                channelId = channel.id,
                                                ruleId = event.ruleId,
                                                value = enabled,
                                            ),
                                    )
                                }
                            },
                        )
                        if (overridden) {
                            TextButton(
                                onClick = {
                                    update { current ->
                                        current.copy(
                                            notificationPreferences =
                                                current.notificationPreferences
                                                    .clearChannelEventOverride(
                                                        channelId = channel.id,
                                                        ruleId = event.ruleId,
                                                    ),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.notifications_use_global))
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }

        TextButton(
            onClick = {
                when (notificationAction) {
                    NotificationPermissionAction.REQUEST_PERMISSION -> onRequestNotificationPermission()
                    NotificationPermissionAction.OPEN_SETTINGS -> onOpenNotificationSettings()
                    NotificationPermissionAction.NONE -> Unit
                }
            },
            enabled = notificationAction != NotificationPermissionAction.NONE,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (notificationAction) {
                    NotificationPermissionAction.REQUEST_PERMISSION -> stringResource(Res.string.notifications_enable)
                    NotificationPermissionAction.OPEN_SETTINGS -> stringResource(Res.string.notifications_open_settings)
                    NotificationPermissionAction.NONE -> stringResource(Res.string.notifications_enabled)
                },
            )
        }
        FerventioPushSettingsSection()
    }
}

@Composable
private fun notificationEventLabel(event: NotificationEventType): String = when (event) {
    NotificationEventType.MENTION -> stringResource(Res.string.notification_event_mention)
    NotificationEventType.REPLY -> stringResource(Res.string.notification_event_reply)
    NotificationEventType.AUTOMOD_HOLD -> stringResource(Res.string.notification_event_automod_hold)
    NotificationEventType.BAN -> stringResource(Res.string.notification_event_ban)
    NotificationEventType.TIMEOUT -> stringResource(Res.string.notification_event_timeout)
    NotificationEventType.HIGHLIGHT -> stringResource(Res.string.notification_event_highlight)
    NotificationEventType.SELECTED_USER -> stringResource(Res.string.notification_event_selected_user)
    NotificationEventType.STREAM_ONLINE -> stringResource(Res.string.notification_event_stream_online)
    NotificationEventType.TITLE_CHANGE -> stringResource(Res.string.notification_event_title_change)
    NotificationEventType.GAME_CHANGE -> stringResource(Res.string.notification_event_game_change)
    NotificationEventType.RAID -> stringResource(Res.string.notification_event_raid)
    NotificationEventType.REWARD -> stringResource(Res.string.notification_event_reward)
    NotificationEventType.SUBSCRIPTION -> stringResource(Res.string.notification_event_subscription)
    NotificationEventType.MODERATION_ACTION -> stringResource(Res.string.notification_event_moderation_action)
}

@Composable
private fun HistorySettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    SettingsSwitchRow(
        label = stringResource(Res.string.settings_recent_messages),
        checked = preferences.recentMessagesEnabled,
        onCheckedChange = { value -> update { it.copy(recentMessagesEnabled = value) } },
    )
    SettingsSwitchRow(
        label = stringResource(Res.string.settings_local_history),
        checked = preferences.localHistoryEnabled,
        onCheckedChange = { value -> update { it.copy(localHistoryEnabled = value) } },
    )
    if (preferences.localHistoryEnabled) {
        Text(
            text = stringResource(Res.string.settings_history_limit, preferences.localHistoryLimit),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = preferences.localHistoryLimit.toFloat(),
            onValueChange = { raw ->
                val value = ((raw / 100f).roundToInt() * 100).coerceIn(100, 5_000)
                update { it.copy(localHistoryLimit = value) }
            },
            valueRange = 100f..5_000f,
        )
        Text(
            text = if (preferences.localHistoryRetentionDays == 0) {
                stringResource(Res.string.settings_history_retention_unlimited)
            } else {
                stringResource(Res.string.settings_history_retention, preferences.localHistoryRetentionDays)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = preferences.localHistoryRetentionDays.toFloat(),
            onValueChange = { raw ->
                update { it.copy(localHistoryRetentionDays = raw.roundToInt().coerceIn(0, 365)) }
            },
            valueRange = 0f..365f,
        )
        Text(
            text = if (preferences.localHistoryMaxSizeMb == 0) {
                stringResource(Res.string.settings_history_max_size_unlimited)
            } else {
                stringResource(Res.string.settings_history_max_size, preferences.localHistoryMaxSizeMb)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = preferences.localHistoryMaxSizeMb.toFloat(),
            onValueChange = { raw ->
                update { it.copy(localHistoryMaxSizeMb = raw.roundToInt().coerceIn(0, 1_024)) }
            },
            valueRange = 0f..1_024f,
        )
    }
}

@Composable
private fun UserCardSettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    FerventioSettingsSection(
        title = stringResource(Res.string.settings_user_card),
    ) {
        SettingsSwitchRow(
            label = stringResource(Res.string.settings_user_card_show_ban),
            checked = preferences.userCardShowBanAction,
            onCheckedChange = { value -> update { it.copy(userCardShowBanAction = value) } },
        )
        UserCardModerationSettingsEditor(
            preferences = preferences,
            onPreferencesChange = { next -> update { next } },
        )
    }
}

@Composable
private fun SettingsSaveState(state: SharedAppSettingsStateHolder) {
    when (state.saveStatus) {
        SharedSettingsSaveStatus.SAVING -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(stringResource(Res.string.settings_saving))
        }
        SharedSettingsSaveStatus.FAILED -> Text(
            text = stringResource(Res.string.settings_save_failed, state.saveErrorMessage.orEmpty()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 18.dp),
        )
        SharedSettingsSaveStatus.IDLE -> Unit
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SettingsChoiceGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val buttonModifier = Modifier
                .widthIn(min = 84.dp)
                .heightIn(min = 48.dp)
            if (value == selected) {
                FilledTonalButton(onClick = { onSelected(value) }, modifier = buttonModifier) {
                    Text(label, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelected(value) }, modifier = buttonModifier) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SettingsMentionColorPicker(selectedArgb: Long, onSelected: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MentionColors.presets.forEach { argb ->
            val selected = argb == selectedArgb
            Surface(
                modifier = Modifier.size(if (selected) 36.dp else 32.dp).clickable { onSelected(argb) },
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(argb.toInt()),
                border = BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Box(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
