package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.MessageDensity
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.push.PushAuthorizationStatus
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
    HISTORY,
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
    onOpenMessageRules: () -> Unit = {},
    onOpenSavedFilters: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var persistedPreferences by remember { mutableStateOf(state.preferences) }
    val notificationAction = notificationPermissionAction(notificationAuthorizationStatus)
    val aboutInfo = LocalFerventioAboutInfo.current
    val accountActions = LocalFerventioAccountActions.current
    val privacyPlatformInfo = LocalFerventioPrivacyPlatformInfo.current
    var page by remember { mutableStateOf(SharedSettingsPage.ROOT) }

    fun update(transform: (SharedAppPreferences) -> SharedAppPreferences) {
        state.updateLocally(transform)
    }

    fun persistIfChanged() {
        if (state.preferences != persistedPreferences) {
            persistedPreferences = state.preferences
            onSave(state.preferences)
        }
    }

    fun saveAndDismiss() {
        persistIfChanged()
        onDismiss()
    }

    fun openMessageRules() {
        persistIfChanged()
        onDismiss()
        onOpenMessageRules()
    }

    fun openSavedFilters() {
        persistIfChanged()
        onDismiss()
        onOpenSavedFilters()
    }

    ModalBottomSheet(onDismissRequest = ::saveAndDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SettingsPageHeader(
                page = page,
                onBack = {
                    page = when (page) {
                        SharedSettingsPage.PRIVACY,
                        SharedSettingsPage.LICENSES,
                        -> SharedSettingsPage.ABOUT
                        else -> SharedSettingsPage.ROOT
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            when (page) {
                SharedSettingsPage.ROOT -> {
                    if (accountActions.accountManagementAvailable) {
                        FerventioSettingsAccountProfileCard(
                            onOpenAccount = { page = SharedSettingsPage.ACCOUNT },
                            onSignOut = {
                                saveAndDismiss()
                                accountActions.onSignOut()
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    SettingsHome(
                        preferences = state.preferences,
                        aboutInfo = aboutInfo,
                        onOpen = { page = it },
                        onOpenMessageRules = ::openMessageRules,
                        onOpenSavedFilters = ::openSavedFilters,
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
                    notificationAction = notificationAction,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    update = ::update,
                )
                SharedSettingsPage.HISTORY -> HistorySettingsPage(
                    preferences = state.preferences,
                    update = ::update,
                )
                SharedSettingsPage.ACCOUNT -> FerventioAccountSettingsPage()
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
            TextButton(
                onClick = ::saveAndDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(stringResource(Res.string.settings_close))
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(page: SharedSettingsPage, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (page != SharedSettingsPage.ROOT) {
            TextButton(onClick = onBack) {
                Text("‹ ${stringResource(Res.string.settings_back)}")
            }
        }
        Text(
            text = when (page) {
                SharedSettingsPage.ROOT -> stringResource(Res.string.settings_title)
                SharedSettingsPage.APPEARANCE -> stringResource(Res.string.settings_messages_appearance)
                SharedSettingsPage.CHAT -> stringResource(Res.string.settings_input_behavior)
                SharedSettingsPage.USER_CARD -> stringResource(Res.string.settings_user_card)
                SharedSettingsPage.NOTIFICATIONS -> stringResource(Res.string.notifications_title)
                SharedSettingsPage.HISTORY -> stringResource(Res.string.settings_history)
                SharedSettingsPage.ACCOUNT -> stringResource(Res.string.settings_account)
                SharedSettingsPage.LANGUAGE -> stringResource(Res.string.settings_language)
                SharedSettingsPage.ABOUT -> stringResource(Res.string.settings_about)
                SharedSettingsPage.PRIVACY -> stringResource(Res.string.about_privacy_policy)
                SharedSettingsPage.LICENSES -> stringResource(Res.string.about_open_source_licenses)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsHome(
    preferences: SharedAppPreferences,
    aboutInfo: FerventioAboutInfo,
    onOpen: (SharedSettingsPage) -> Unit,
    onOpenMessageRules: () -> Unit,
    onOpenSavedFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsHomeGroup(stringResource(Res.string.settings_home_chat_group)) {
            SettingsMenuRow(
                title = stringResource(Res.string.settings_messages_appearance),
                summary = stringResource(Res.string.settings_messages_appearance_summary),
                onClick = { onOpen(SharedSettingsPage.APPEARANCE) },
            )
            HorizontalDivider()
            SettingsMenuRow(
                title = stringResource(Res.string.settings_input_behavior),
                summary = stringResource(Res.string.settings_input_behavior_summary),
                onClick = { onOpen(SharedSettingsPage.CHAT) },
            )
            HorizontalDivider()
            SettingsMenuRow(
                title = stringResource(Res.string.settings_user_card),
                summary = stringResource(Res.string.settings_user_card_summary),
                onClick = { onOpen(SharedSettingsPage.USER_CARD) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_features_group)) {
            SettingsMenuRow(
                title = stringResource(Res.string.message_rules_title),
                summary = stringResource(Res.string.settings_message_rules_summary),
                onClick = onOpenMessageRules,
            )
            HorizontalDivider()
            SettingsMenuRow(
                title = stringResource(Res.string.saved_filters_title),
                summary = stringResource(Res.string.saved_filters_summary),
                onClick = onOpenSavedFilters,
            )
            HorizontalDivider()
            SettingsMenuRow(
                title = stringResource(Res.string.notifications_title),
                summary = stringResource(Res.string.settings_notifications_summary),
                onClick = { onOpen(SharedSettingsPage.NOTIFICATIONS) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_data_group)) {
            SettingsMenuRow(
                title = stringResource(Res.string.settings_history),
                summary = stringResource(Res.string.settings_history_summary),
                onClick = { onOpen(SharedSettingsPage.HISTORY) },
            )
        }

        SettingsHomeGroup(stringResource(Res.string.settings_home_app_group)) {
            SettingsMenuRow(
                title = stringResource(Res.string.settings_language),
                summary = sharedLanguageLabel(preferences.appLanguage),
                onClick = { onOpen(SharedSettingsPage.LANGUAGE) },
            )
            HorizontalDivider()
            SettingsMenuRow(
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
private fun SettingsHomeGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsMenuRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    SettingsSectionTitle(stringResource(Res.string.settings_appearance))
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
    Text(
        text = stringResource(Res.string.settings_font_size, preferences.fontScalePercent),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = preferences.fontScalePercent.toFloat(),
        onValueChange = { raw ->
            val value = (raw / 5f).roundToInt() * 5
            update { it.copy(fontScalePercent = value) }
        },
        valueRange = 80f..150f,
    )

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
        label = stringResource(Res.string.settings_wrap_messages),
        checked = preferences.wrapMessageLines,
        onCheckedChange = { value -> update { it.copy(wrapMessageLines = value) } },
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
    Text(
        text = stringResource(Res.string.settings_mention_color),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp),
    )
    SettingsMentionColorPicker(
        selectedArgb = preferences.mentionColorArgb,
        onSelected = { value -> update { it.copy(mentionColorArgb = value) } },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    SettingsSectionTitle(stringResource(Res.string.settings_media))
    SettingsSwitchRow(
        label = stringResource(Res.string.settings_animate_emotes),
        checked = preferences.animateEmotes,
        onCheckedChange = { value -> update { it.copy(animateEmotes = value) } },
    )
    Text(
        text = stringResource(Res.string.settings_emote_size, preferences.emoteScalePercent),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = preferences.emoteScalePercent.toFloat(),
        onValueChange = { raw ->
            val value = (raw / 5f).roundToInt() * 5
            update { it.copy(emoteScalePercent = value) }
        },
        valueRange = 75f..200f,
    )
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
}

@Composable
private fun ChatBehaviorSettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    SettingsSectionTitle(stringResource(Res.string.settings_chat))
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
    QuickModerationSettingsSection()

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    SettingsSectionTitle(stringResource(Res.string.settings_composer))
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
}

@Composable
private fun NotificationsSettingsPage(
    preferences: SharedAppPreferences,
    notificationAction: NotificationPermissionAction,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    SettingsSwitchRow(
        label = stringResource(Res.string.settings_reply_notifications),
        checked = preferences.replyNotificationsEnabled,
        onCheckedChange = { value -> update { it.copy(replyNotificationsEnabled = value) } },
    )
    SettingsSwitchRow(
        label = stringResource(Res.string.settings_automod_notifications),
        checked = preferences.autoModNotificationsEnabled,
        onCheckedChange = { value -> update { it.copy(autoModNotificationsEnabled = value) } },
    )
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
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp),
    )
    options.forEach { (value, label) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == selected, onClick = { onSelected(value) })
            Text(label, style = MaterialTheme.typography.bodyMedium)
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
