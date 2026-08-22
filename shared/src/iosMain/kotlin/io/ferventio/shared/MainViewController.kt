package io.ferventio.shared

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.history.IosChatHistoryStore
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.ProvideFerventioRuntimeState
import io.ferventio.shared.settings.IosLocalUiPreferencesStore
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedLocalUiPreferencesStateHolder
import io.ferventio.shared.ui.app.FerventioAboutInfo
import io.ferventio.shared.ui.app.FerventioAuthenticationRoot
import io.ferventio.shared.ui.app.ProvideFerventioAboutInfo
import io.ferventio.shared.ui.locale.FerventioLocaleEnvironment
import io.ferventio.shared.ui.theme.FerventioTheme
import io.ferventio.shared.ui.theme.FerventioThemeMode
import platform.UIKit.UIViewController

private val iosRuntimeState = FerventioRuntimeState(
    history = IosChatHistoryStore(),
    localUiPreferences = SharedLocalUiPreferencesStateHolder(IosLocalUiPreferencesStore()),
)

fun IosRuntimeState(): FerventioRuntimeState = iosRuntimeState

fun MainViewController(
    onAuthenticate: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onAuthenticationRequired: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSaveSettings: (SharedAppPreferences) -> Unit = {},
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
    aboutVersionName: String = "",
    aboutWebsiteUrl: String = "",
    aboutGithubUrl: String = "",
    aboutTelegramChannelUrl: String = "",
    aboutTelegramChatUrl: String = "",
    aboutTranslationsUrl: String = "",
    aboutPrivacyOperatorName: String = "",
    aboutPrivacyContact: String = "",
    aboutPrivacyPolicyUrl: String = "",
    aboutShowPrivacyPolicyInApp: Boolean = false,
): UIViewController = ComposeUIViewController {
    val preferences = iosRuntimeState.settings.preferences
    val authenticationRequired = iosRuntimeState.chat.authenticationRequired
    val aboutInfo = FerventioAboutInfo(
        versionName = aboutVersionName,
        websiteUrl = aboutWebsiteUrl,
        githubUrl = aboutGithubUrl,
        telegramChannelUrl = aboutTelegramChannelUrl,
        telegramChatUrl = aboutTelegramChatUrl,
        translationsUrl = aboutTranslationsUrl,
        privacyOperatorName = aboutPrivacyOperatorName,
        privacyContact = aboutPrivacyContact,
        privacyPolicyUrl = aboutPrivacyPolicyUrl,
        showPrivacyPolicyInApp = aboutShowPrivacyPolicyInApp,
    )
    LaunchedEffect(authenticationRequired) {
        if (authenticationRequired) onAuthenticationRequired()
    }
    ProvideFerventioRuntimeState(iosRuntimeState) {
        ProvideFerventioAboutInfo(aboutInfo) {
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
                        state = iosRuntimeState.authentication.state,
                        workspace = iosRuntimeState.workspace,
                        onAuthenticate = onAuthenticate,
                        onSignOut = onSignOut,
                        pushAuthorizationStatus = iosRuntimeState.pushRegistration.authorizationStatus,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onSaveSettings = onSaveSettings,
                        onUpsertHighlightRule = onUpsertHighlightRule,
                        onDeleteHighlightRule = onDeleteHighlightRule,
                        onUpsertIgnoreRule = onUpsertIgnoreRule,
                        onDeleteIgnoreRule = onDeleteIgnoreRule,
                        onUpsertSavedFilter = onUpsertSavedFilter,
                        onDeleteSavedFilter = onDeleteSavedFilter,
                        onImportSavedFilters = onImportSavedFilters,
                        onAddSavedFilterSplit = onAddSavedFilterSplit,
                        onSelectChannel = onSelectChannel,
                        onAddChannel = onAddChannel,
                        onSetChannelPinned = onSetChannelPinned,
                        onRenameChannel = onRenameChannel,
                        onRemoveChannel = onRemoveChannel,
                        onMoveChannel = onMoveChannel,
                        onSetSplitFilterQuery = onSetSplitFilterQuery,
                        onSetSplitChannel = onSetSplitChannel,
                        onFocusSplit = onFocusSplit,
                        onAddSplit = onAddSplit,
                        onRemoveSplit = onRemoveSplit,
                        onSetPrimaryFraction = onSetPrimaryFraction,
                    )
                }
            }
        }
    }
}
