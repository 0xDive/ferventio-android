package io.ferventio.shared

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.auth.MobileAuthenticationStatus
import io.ferventio.shared.chat.AnonymousChatRuntimeCoordinator
import io.ferventio.shared.history.IosChatHistoryStore
import io.ferventio.shared.runtime.AppLifecyclePhase
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.ProvideFerventioRuntimeState
import io.ferventio.shared.settings.AnonymousHistoryPreferencesCoordinator
import io.ferventio.shared.settings.IosAnonymousHistoryPreferencesStore
import io.ferventio.shared.settings.IosLocalUiPreferencesStore
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedLocalUiPreferencesStateHolder
import io.ferventio.shared.ui.app.FerventioAccountActions
import io.ferventio.shared.ui.app.FerventioAuthenticationRoot
import io.ferventio.shared.ui.app.FerventioSettingsBackupActions
import io.ferventio.shared.ui.app.FerventioSettingsBackupOperationFeedback
import io.ferventio.shared.ui.app.ProvideFerventioAboutInfo
import io.ferventio.shared.ui.app.ProvideFerventioAccountActions
import io.ferventio.shared.ui.app.ProvideFerventioPrivacyPlatformInfo
import io.ferventio.shared.ui.app.ProvideFerventioSettingsBackupActions
import io.ferventio.shared.ui.app.SharedSettingsBackupStatus
import io.ferventio.shared.ui.app.currentIosPrivacyPlatformInfo
import io.ferventio.shared.ui.locale.FerventioLocaleEnvironment
import io.ferventio.shared.ui.theme.FerventioTheme
import io.ferventio.shared.ui.theme.FerventioThemeMode
import io.ferventio.shared.workspace.AnonymousWorkspaceCoordinator
import io.ferventio.shared.workspace.IosAnonymousWorkspaceStore
import io.ferventio.shared.workspace.WorkspaceLoadStatus
import platform.UIKit.UIViewController

private val iosRuntimeState = FerventioRuntimeState(
    history = IosChatHistoryStore(),
    localUiPreferences = SharedLocalUiPreferencesStateHolder(IosLocalUiPreferencesStore()),
)
private val iosSettingsBackupRuntime = IosSettingsBackupRuntime(iosRuntimeState)
private val iosAnonymousWorkspaceCoordinator = AnonymousWorkspaceCoordinator(
    IosAnonymousWorkspaceStore(),
)
private val iosAnonymousHistoryPreferencesCoordinator = AnonymousHistoryPreferencesCoordinator(
    IosAnonymousHistoryPreferencesStore(),
)
private val iosAnonymousChatRuntime = AnonymousChatRuntimeCoordinator(
    state = iosRuntimeState.chat,
    attention = iosRuntimeState.attention,
    historyStore = iosRuntimeState.history,
    settings = iosRuntimeState.settings,
    messageRules = iosRuntimeState.messageRules,
)

fun IosRuntimeState(): FerventioRuntimeState = iosRuntimeState

fun IosSettingsBackupRuntimeState(): IosSettingsBackupRuntime = iosSettingsBackupRuntime

fun MainViewController(
    onAuthenticate: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onAuthenticationRequired: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSaveSettings: (SharedAppPreferences) -> Unit = {},
    onExportSettingsBackup: (() -> Unit)? = null,
    onImportSettingsBackup: (() -> Unit)? = null,
    onKeepLocalSettingsBackup: (() -> Unit)? = null,
    onUseServerSettingsBackup: (() -> Unit)? = null,
    onUpsertHighlightRule: (HighlightRule) -> Unit = {},
    onDeleteHighlightRule: (String) -> Unit = {},
    onUpsertIgnoreRule: (IgnoreRule) -> Unit = {},
    onDeleteIgnoreRule: (String) -> Unit = {},
    onUpsertSavedFilter: (SavedMessageFilter) -> Unit = {},
    onDeleteSavedFilter: (String) -> Unit = {},
    onImportSavedFilters: (String) -> Unit = {},
    onAddSavedFilterSplit: (String) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onSetChannelPinned: (String, Boolean) -> Unit = { _, _ -> },
    onRenameChannel: (String, String?) -> Unit = { _, _ -> },
    onRemoveChannel: (String) -> Unit = {},
    onMoveChannel: (String, Int) -> Unit = { _, _ -> },
    onSetSplitFilterQuery: (String, String) -> Unit = { _, _ -> },
    onSetSplitChannel: (String, String) -> Unit = { _, _ -> },
    onFocusSplit: (String) -> Unit = {},
    onAddSplit: () -> Unit = {},
    onRemoveSplit: (String) -> Unit = {},
    onSetPrimaryFraction: (Float) -> Unit = {},
    onReauthorize: (() -> Unit)? = null,
    onRevokeDevice: (() -> Unit)? = null,
    onRevokeAllSessions: (() -> Unit)? = null,
): UIViewController = ComposeUIViewController {
    val preferences = iosRuntimeState.settings.preferences
    val authenticationState = iosRuntimeState.authentication.state
    val anonymousMode = authenticationState.status == MobileAuthenticationStatus.SIGNED_OUT ||
        authenticationState.status == MobileAuthenticationStatus.FAILED
    val lifecyclePhase = iosRuntimeState.lifecycle.phase
    val anonymousTransportLogins = iosRuntimeState.workspace.channels
        .map { channel -> channel.login.trim().lowercase() }
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
    val authenticationRequired = iosRuntimeState.chat.authenticationRequired
    val backupStatus = iosSettingsBackupRuntime.state.status
    val backupLocked = backupStatus == SharedSettingsBackupStatus.EXPORTING ||
        backupStatus == SharedSettingsBackupStatus.IMPORTING ||
        backupStatus == SharedSettingsBackupStatus.RESOLVING ||
        backupStatus == SharedSettingsBackupStatus.CONFLICT
    val aboutInfo = remember { currentIosAboutInfo() }
    val privacyPlatformInfo = remember { currentIosPrivacyPlatformInfo() }
    val accountActions = remember(onReauthorize, onSignOut, onRevokeDevice, onRevokeAllSessions) {
        FerventioAccountActions(
            onReauthorize = onReauthorize,
            onSignOut = onSignOut,
            onRevokeDevice = onRevokeDevice,
            onRevokeAllSessions = onRevokeAllSessions,
        )
    }
    val backupActions = remember(
        onExportSettingsBackup,
        onImportSettingsBackup,
        onKeepLocalSettingsBackup,
        onUseServerSettingsBackup,
        backupStatus,
    ) {
        FerventioSettingsBackupActions(
            state = iosSettingsBackupRuntime.state,
            onExport = if (backupLocked) null else onExportSettingsBackup,
            onImport = if (backupLocked) null else onImportSettingsBackup,
            onKeepLocal = if (backupStatus == SharedSettingsBackupStatus.CONFLICT) {
                onKeepLocalSettingsBackup
            } else {
                null
            },
            onUseServer = if (backupStatus == SharedSettingsBackupStatus.CONFLICT) {
                onUseServerSettingsBackup
            } else {
                null
            },
        )
    }

    LaunchedEffect(authenticationRequired) {
        if (authenticationRequired) onAuthenticationRequired()
    }
    LaunchedEffect(anonymousMode) {
        if (anonymousMode) {
            runCatching {
                iosAnonymousHistoryPreferencesCoordinator.restore(iosRuntimeState.settings)
            }
        }
    }
    LaunchedEffect(anonymousMode, lifecyclePhase, anonymousTransportLogins) {
        if (!anonymousMode) {
            iosAnonymousChatRuntime.close()
            return@LaunchedEffect
        }

        if (iosRuntimeState.workspace.loadStatus != WorkspaceLoadStatus.READY) {
            runCatching {
                iosAnonymousHistoryPreferencesCoordinator.restore(iosRuntimeState.settings)
                iosAnonymousWorkspaceCoordinator.restore(iosRuntimeState.workspace)
            }
            return@LaunchedEffect
        }
        if (lifecyclePhase != AppLifecyclePhase.ACTIVE || iosRuntimeState.workspace.channels.isEmpty()) {
            iosAnonymousChatRuntime.close()
            return@LaunchedEffect
        }
        runCatching {
            iosAnonymousChatRuntime.run(iosRuntimeState.workspace)
        }
    }

    val selectChannelAction: (String) -> Unit = { channelId ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.selectChannel(channelId, iosRuntimeState.workspace)
            }
        } else {
            onSelectChannel(channelId)
        }
    }
    val addChannelAction: (String) -> Unit = { login ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.addChannel(login, iosRuntimeState.workspace)
            }
        } else {
            onAddChannel(login)
        }
    }
    val setChannelPinnedAction: (String, Boolean) -> Unit = { channelId, pinned ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.setChannelPinned(
                    channelId,
                    pinned,
                    iosRuntimeState.workspace,
                )
            }
        } else {
            onSetChannelPinned(channelId, pinned)
        }
    }
    val renameChannelAction: (String, String?) -> Unit = { channelId, title ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.renameChannel(
                    channelId,
                    title,
                    iosRuntimeState.workspace,
                )
            }
        } else {
            onRenameChannel(channelId, title)
        }
    }
    val removeChannelAction: (String) -> Unit = { channelId ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.removeChannel(channelId, iosRuntimeState.workspace)
                iosRuntimeState.chat.retainChannels(iosRuntimeState.workspace.channelIds)
                iosRuntimeState.attention.retainChannels(iosRuntimeState.workspace.channelIds)
            }
        } else {
            onRemoveChannel(channelId)
        }
    }
    val moveChannelAction: (String, Int) -> Unit = { channelId, targetIndex ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.moveChannel(
                    channelId,
                    targetIndex,
                    iosRuntimeState.workspace,
                )
            }
        } else {
            onMoveChannel(channelId, targetIndex)
        }
    }
    val setSplitFilterQueryAction: (String, String) -> Unit = { splitId, filterQuery ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.setSplitFilterQuery(
                    splitId,
                    filterQuery,
                    iosRuntimeState.workspace,
                )
            }
        } else {
            onSetSplitFilterQuery(splitId, filterQuery)
        }
    }
    val setSplitChannelAction: (String, String) -> Unit = { splitId, channelId ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.setSplitChannel(
                    splitId,
                    channelId,
                    iosRuntimeState.workspace,
                )
            }
        } else {
            onSetSplitChannel(splitId, channelId)
        }
    }
    val focusSplitAction: (String) -> Unit = { splitId ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.focusSplit(splitId, iosRuntimeState.workspace)
            }
        } else {
            onFocusSplit(splitId)
        }
    }
    val addSplitAction: () -> Unit = {
        if (anonymousMode) {
            runCatching { iosAnonymousWorkspaceCoordinator.addSplit(iosRuntimeState.workspace) }
        } else {
            onAddSplit()
        }
    }
    val removeSplitAction: (String) -> Unit = { splitId ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.removeSplit(splitId, iosRuntimeState.workspace)
            }
        } else {
            onRemoveSplit(splitId)
        }
    }
    val setPrimaryFractionAction: (Float) -> Unit = { fraction ->
        if (anonymousMode) {
            runCatching {
                iosAnonymousWorkspaceCoordinator.setPrimaryFraction(fraction, iosRuntimeState.workspace)
            }
        } else {
            onSetPrimaryFraction(fraction)
        }
    }
    val saveAnonymousHistoryPreferencesAction: (SharedAppPreferences) -> Unit = { value ->
        if (anonymousMode) {
            runCatching { iosAnonymousHistoryPreferencesCoordinator.save(value) }
        }
    }

    ProvideFerventioRuntimeState(iosRuntimeState) {
        ProvideFerventioAboutInfo(aboutInfo) {
            ProvideFerventioAccountActions(accountActions) {
                ProvideFerventioSettingsBackupActions(backupActions) {
                    ProvideFerventioPrivacyPlatformInfo(privacyPlatformInfo) {
                        FerventioLocaleEnvironment(preferences.appLanguage) {
                            FerventioTheme(
                                themeMode = when (preferences.themeMode) {
                                    AppThemeMode.LIGHT -> FerventioThemeMode.LIGHT
                                    AppThemeMode.DARK -> FerventioThemeMode.DARK
                                    AppThemeMode.AMOLED -> FerventioThemeMode.AMOLED
                                },
                                fontScalePercent = preferences.fontScalePercent,
                            ) {
                                FerventioAuthenticationRoot(
                                    state = authenticationState,
                                    workspace = iosRuntimeState.workspace,
                                    onAuthenticate = onAuthenticate,
                                    onSignOut = onSignOut,
                                    pushAuthorizationStatus = iosRuntimeState.pushRegistration.authorizationStatus,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    onSaveSettings = onSaveSettings,
                                    onSaveAnonymousHistoryPreferences = saveAnonymousHistoryPreferencesAction,
                                    onUpsertHighlightRule = onUpsertHighlightRule,
                                    onDeleteHighlightRule = onDeleteHighlightRule,
                                    onUpsertIgnoreRule = onUpsertIgnoreRule,
                                    onDeleteIgnoreRule = onDeleteIgnoreRule,
                                    onUpsertSavedFilter = onUpsertSavedFilter,
                                    onDeleteSavedFilter = onDeleteSavedFilter,
                                    onImportSavedFilters = onImportSavedFilters,
                                    onAddSavedFilterSplit = onAddSavedFilterSplit,
                                    onSelectChannel = selectChannelAction,
                                    onAddChannel = addChannelAction,
                                    onSetChannelPinned = setChannelPinnedAction,
                                    onRenameChannel = renameChannelAction,
                                    onRemoveChannel = removeChannelAction,
                                    onMoveChannel = moveChannelAction,
                                    onSetSplitFilterQuery = setSplitFilterQueryAction,
                                    onSetSplitChannel = setSplitChannelAction,
                                    onFocusSplit = focusSplitAction,
                                    onAddSplit = addSplitAction,
                                    onRemoveSplit = removeSplitAction,
                                    onSetPrimaryFraction = setPrimaryFractionAction,
                                )
                                FerventioSettingsBackupOperationFeedback(backupActions)
                            }
                        }
                    }
                }
            }
        }
    }
}
