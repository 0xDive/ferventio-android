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
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.MessageDensity
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Device-local signed-out settings. The historical function name is kept as a compatibility shim
 * while anonymous settings grow beyond the original History-only surface.
 */
@Composable
internal fun FerventioAnonymousHistorySettingsSheet(
    state: SharedAppSettingsStateHolder,
    onSave: (SharedAppPreferences) -> Unit,
    onDismiss: () -> Unit,
) = FerventioAnonymousSettingsSheet(
    state = state,
    onSave = onSave,
    onDismiss = onDismiss,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioAnonymousSettingsSheet(
    state: SharedAppSettingsStateHolder,
    onSave: (SharedAppPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var persistedPreferences by remember { mutableStateOf(state.preferences) }

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

    ModalBottomSheet(onDismissRequest = ::saveAndDismiss) {
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
            Spacer(Modifier.height(16.dp))

            AnonymousSettingsSectionTitle(stringResource(Res.string.settings_messages_appearance))
            AnonymousChoiceGroup(
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
            AnonymousChoiceGroup(
                title = stringResource(Res.string.settings_density),
                options = listOf(
                    MessageDensity.COMPACT to stringResource(Res.string.settings_density_compact),
                    MessageDensity.NORMAL to stringResource(Res.string.settings_density_normal),
                    MessageDensity.RELAXED to stringResource(Res.string.settings_density_relaxed),
                ),
                selected = state.preferences.messageDensity,
                onSelected = { value -> update { it.copy(messageDensity = value) } },
            )
            AnonymousChoiceGroup(
                title = stringResource(Res.string.settings_name_style),
                options = listOf(
                    ChatNameStyle.DISPLAY_NAME to stringResource(Res.string.settings_name_display),
                    ChatNameStyle.LOGIN to stringResource(Res.string.settings_name_login),
                    ChatNameStyle.DISPLAY_AND_LOGIN to stringResource(Res.string.settings_name_both),
                ),
                selected = state.preferences.nameStyle,
                onSelected = { value -> update { it.copy(nameStyle = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_show_avatars),
                checked = state.preferences.showAvatars,
                onCheckedChange = { value -> update { it.copy(showAvatars = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_show_badges),
                checked = state.preferences.showBadges,
                onCheckedChange = { value -> update { it.copy(showBadges = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_show_timestamps),
                checked = state.preferences.showTimestamps,
                onCheckedChange = { value -> update { it.copy(showTimestamps = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_wrap_messages),
                checked = state.preferences.wrapMessageLines,
                onCheckedChange = { value -> update { it.copy(wrapMessageLines = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_show_deleted_content),
                checked = state.preferences.showDeletedMessageContent,
                onCheckedChange = { value -> update { it.copy(showDeletedMessageContent = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_show_system_messages),
                checked = state.preferences.showSystemMessages,
                onCheckedChange = { value -> update { it.copy(showSystemMessages = value) } },
            )
            Text(
                text = stringResource(Res.string.settings_mention_color),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            AnonymousMentionColorPicker(
                selectedArgb = state.preferences.mentionColorArgb,
                onSelected = { value -> update { it.copy(mentionColorArgb = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            AnonymousSettingsSectionTitle(stringResource(Res.string.settings_media))
            AnonymousSwitchRow(
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
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_bttv),
                checked = state.preferences.betterTtvEnabled,
                onCheckedChange = { value -> update { it.copy(betterTtvEnabled = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_ffz),
                checked = state.preferences.frankerFaceZEnabled,
                onCheckedChange = { value -> update { it.copy(frankerFaceZEnabled = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_7tv),
                checked = state.preferences.sevenTvEnabled,
                onCheckedChange = { value -> update { it.copy(sevenTvEnabled = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            AnonymousSettingsSectionTitle(stringResource(Res.string.settings_input_behavior))
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_auto_scroll),
                checked = state.preferences.autoScrollEnabled,
                onCheckedChange = { value -> update { it.copy(autoScrollEnabled = value) } },
            )
            AnonymousSwitchRow(
                label = stringResource(Res.string.settings_repeat_collapse),
                checked = state.preferences.repeatCollapseEnabled,
                onCheckedChange = { value -> update { it.copy(repeatCollapseEnabled = value) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            FerventioHistorySettingsPage(
                preferences = state.preferences,
                update = ::update,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            FerventioLanguageSettingsPage(
                preferences = state.preferences,
                onLanguageSelected = { language ->
                    update { it.copy(appLanguage = language) }
                },
            )

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = ::saveAndDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_close))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AnonymousSettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> AnonymousChoiceGroup(
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
            RadioButton(selected = value == selected, onClick = { onSelected(value) })
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnonymousSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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

@Composable
private fun AnonymousMentionColorPicker(
    selectedArgb: Long,
    onSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
