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
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioSettingsSheet(
    state: SharedAppSettingsStateHolder,
    notificationAuthorizationStatus: PushAuthorizationStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSave: (SharedAppPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialPreferences = remember { state.preferences }
    val notificationAction = notificationPermissionAction(notificationAuthorizationStatus)

    fun update(transform: (SharedAppPreferences) -> SharedAppPreferences) {
        state.updateLocally(transform)
    }

    fun saveAndDismiss() {
        if (state.preferences != initialPreferences) {
            onSave(state.preferences)
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::saveAndDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            SettingsSectionTitle(stringResource(Res.string.settings_appearance))
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_language),
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(Res.string.settings_language_system),
                    AppLanguage.RUSSIAN to stringResource(Res.string.settings_language_russian),
                    AppLanguage.ENGLISH to stringResource(Res.string.settings_language_english),
                ),
                selected = state.preferences.appLanguage,
                onSelected = { value -> update { it.copy(appLanguage = value) } },
            )
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_theme),
                options = listOf(
                    AppThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
                    AppThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
                    AppThemeMode.AMOLED to stringResource(Res.string.settings_theme_amoled),
                ),
                selected = state.preferences.themeMode,
                onSelected = { value -> update { it.copy(themeMode = value) } },
            )
            Text(
                text = stringResource(
                    Res.string.settings_font_size,
                    state.preferences.fontScalePercent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.preferences.fontScalePercent.toFloat(),
                onValueChange = { raw ->
                    val value = (raw / 5f).roundToInt() * 5
                    update { it.copy(fontScalePercent = value) }
                },
                valueRange = 80f..150f,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.settings_chat))
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_density),
                options = listOf(
                    MessageDensity.COMPACT to stringResource(Res.string.settings_density_compact),
                    MessageDensity.NORMAL to stringResource(Res.string.settings_density_normal),
                    MessageDensity.RELAXED to stringResource(Res.string.settings_density_relaxed),
                ),
                selected = state.preferences.messageDensity,
                onSelected = { value -> update { it.copy(messageDensity = value) } },
            )
            SettingsChoiceGroup(
                title = stringResource(Res.string.settings_name_style),
                options = listOf(
                    ChatNameStyle.DISPLAY_NAME to stringResource(Res.string.settings_name_display),
                    ChatNameStyle.LOGIN to stringResource(Res.string.settings_name_login),
                    ChatNameStyle.DISPLAY_AND_LOGIN to stringResource(Res.string.settings_name_both),
                ),
                selected = state.preferences.nameStyle,
                onSelected = { value -> update { it.copy(nameStyle = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_avatars),
                checked = state.preferences.showAvatars,
                onCheckedChange = { value -> update { it.copy(showAvatars = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_badges),
                checked = state.preferences.showBadges,
                onCheckedChange = { value -> update { it.copy(showBadges = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_timestamps),
                checked = state.preferences.showTimestamps,
                onCheckedChange = { value -> update { it.copy(showTimestamps = value) } },
            )
            QuickModerationSettingsSection()
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_wrap_messages),
                checked = state.preferences.wrapMessageLines,
                onCheckedChange = { value -> update { it.copy(wrapMessageLines = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_deleted_content),
                checked = state.preferences.showDeletedMessageContent,
                onCheckedChange = { value -> update { it.copy(showDeletedMessageContent = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_show_system_messages),
                checked = state.preferences.showSystemMessages,
                onCheckedChange = { value -> update { it.copy(showSystemMessages = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_auto_scroll),
                checked = state.preferences.autoScrollEnabled,
                onCheckedChange = { value -> update { it.copy(autoScrollEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_repeat_collapse),
                checked = state.preferences.repeatCollapseEnabled,
                onCheckedChange = { value -> update { it.copy(repeatCollapseEnabled = value) } },
            )
            Text(
                text = stringResource(Res.string.settings_mention_color),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            SettingsMentionColorPicker(
                selectedArgb = state.preferences.mentionColorArgb,
                onSelected = { value -> update { it.copy(mentionColorArgb = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.settings_media))
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_animate_emotes),
                checked = state.preferences.animateEmotes,
                onCheckedChange = { value -> update { it.copy(animateEmotes = value) } },
            )
            Text(
                text = stringResource(
                    Res.string.settings_emote_size,
                    state.preferences.emoteScalePercent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.preferences.emoteScalePercent.toFloat(),
                onValueChange = { raw ->
                    val value = (raw / 5f).roundToInt() * 5
                    update { it.copy(emoteScalePercent = value) }
                },
                valueRange = 75f..200f,
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_bttv),
                checked = state.preferences.betterTtvEnabled,
                onCheckedChange = { value -> update { it.copy(betterTtvEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_ffz),
                checked = state.preferences.frankerFaceZEnabled,
                onCheckedChange = { value -> update { it.copy(frankerFaceZEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_7tv),
                checked = state.preferences.sevenTvEnabled,
                onCheckedChange = { value -> update { it.copy(sevenTvEnabled = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.settings_composer))
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_send_on_enter),
                checked = state.preferences.sendOnEnter,
                onCheckedChange = { value -> update { it.copy(sendOnEnter = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_composer_emote_images),
                checked = state.preferences.showComposerEmoteImages,
                onCheckedChange = { value -> update { it.copy(showComposerEmoteImages = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.notifications_title))
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_reply_notifications),
                checked = state.preferences.replyNotificationsEnabled,
                onCheckedChange = { value -> update { it.copy(replyNotificationsEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_automod_notifications),
                checked = state.preferences.autoModNotificationsEnabled,
                onCheckedChange = { value -> update { it.copy(autoModNotificationsEnabled = value) } },
            )
            TextButton(
                onClick = {
                    when (notificationAction) {
                        NotificationPermissionAction.REQUEST_PERMISSION ->
                            onRequestNotificationPermission()
                        NotificationPermissionAction.OPEN_SETTINGS ->
                            onOpenNotificationSettings()
                        NotificationPermissionAction.NONE -> Unit
                    }
                },
                enabled = notificationAction != NotificationPermissionAction.NONE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (notificationAction) {
                        NotificationPermissionAction.REQUEST_PERMISSION ->
                            stringResource(Res.string.notifications_enable)
                        NotificationPermissionAction.OPEN_SETTINGS ->
                            stringResource(Res.string.notifications_open_settings)
                        NotificationPermissionAction.NONE ->
                            stringResource(Res.string.notifications_enabled)
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.settings_history))
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_recent_messages),
                checked = state.preferences.recentMessagesEnabled,
                onCheckedChange = { value -> update { it.copy(recentMessagesEnabled = value) } },
            )
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_local_history),
                checked = state.preferences.localHistoryEnabled,
                onCheckedChange = { value -> update { it.copy(localHistoryEnabled = value) } },
            )
            if (state.preferences.localHistoryEnabled) {
                Text(
                    text = stringResource(
                        Res.string.settings_history_limit,
                        state.preferences.localHistoryLimit,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.preferences.localHistoryLimit.toFloat(),
                    onValueChange = { raw ->
                        val value = ((raw / 100f).roundToInt() * 100).coerceIn(100, 5_000)
                        update { it.copy(localHistoryLimit = value) }
                    },
                    valueRange = 100f..5_000f,
                )
                Text(
                    text = if (state.preferences.localHistoryRetentionDays == 0) {
                        stringResource(Res.string.settings_history_retention_unlimited)
                    } else {
                        stringResource(
                            Res.string.settings_history_retention,
                            state.preferences.localHistoryRetentionDays,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.preferences.localHistoryRetentionDays.toFloat(),
                    onValueChange = { raw ->
                        update {
                            it.copy(localHistoryRetentionDays = raw.roundToInt().coerceIn(0, 365))
                        }
                    },
                    valueRange = 0f..365f,
                )
                Text(
                    text = if (state.preferences.localHistoryMaxSizeMb == 0) {
                        stringResource(Res.string.settings_history_max_size_unlimited)
                    } else {
                        stringResource(
                            Res.string.settings_history_max_size,
                            state.preferences.localHistoryMaxSizeMb,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.preferences.localHistoryMaxSizeMb.toFloat(),
                    onValueChange = { raw ->
                        update {
                            it.copy(localHistoryMaxSizeMb = raw.roundToInt().coerceIn(0, 1_024))
                        }
                    },
                    valueRange = 0f..1_024f,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(Res.string.settings_user_card))
            SettingsSwitchRow(
                label = stringResource(Res.string.settings_user_card_show_ban),
                checked = state.preferences.userCardShowBanAction,
                onCheckedChange = { value -> update { it.copy(userCardShowBanAction = value) } },
            )
            UserCardModerationSettingsEditor(
                preferences = state.preferences,
                onPreferencesChange = { preferences -> update { preferences } },
            )

            when (state.saveStatus) {
                SharedSettingsSaveStatus.SAVING -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.settings_saving))
                }
                SharedSettingsSaveStatus.FAILED -> Text(
                    text = stringResource(
                        Res.string.settings_save_failed,
                        state.saveErrorMessage.orEmpty(),
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                SharedSettingsSaveStatus.IDLE -> Unit
            }

            TextButton(
                onClick = ::saveAndDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(stringResource(Res.string.settings_close))
            }
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = value == selected,
                onClick = { onSelected(value) },
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsMentionColorPicker(
    selectedArgb: Long,
    onSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MentionColors.presets.forEach { argb ->
            val selected = argb == selectedArgb
            Surface(
                modifier = Modifier
                    .size(if (selected) 36.dp else 32.dp)
                    .clickable { onSelected(argb) },
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(argb.toInt()),
                border = BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Box(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
