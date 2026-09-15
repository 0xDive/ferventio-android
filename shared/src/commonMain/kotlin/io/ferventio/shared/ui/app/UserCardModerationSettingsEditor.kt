package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.UserCardModerationLayout
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_user_card_action_ban
import io.ferventio.shared.generated.resources.settings_user_card_action_order
import io.ferventio.shared.generated.resources.settings_user_card_action_timeout
import io.ferventio.shared.generated.resources.settings_user_card_action_unban
import io.ferventio.shared.generated.resources.settings_user_card_action_warn
import io.ferventio.shared.generated.resources.settings_user_card_timeout_add
import io.ferventio.shared.generated.resources.settings_user_card_timeout_hint
import io.ferventio.shared.generated.resources.settings_user_card_timeout_input
import io.ferventio.shared.generated.resources.settings_user_card_timeout_invalid
import io.ferventio.shared.generated.resources.settings_user_card_timeout_presets
import io.ferventio.shared.generated.resources.settings_user_card_timeout_remove
import io.ferventio.shared.generated.resources.settings_user_card_timeout_reset
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.UserCardSettingsEditor
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UserCardModerationSettingsEditor(
    preferences: SharedAppPreferences,
    onPreferencesChange: (SharedAppPreferences) -> Unit,
) {
    val normalized = preferences.normalized()
    var timeoutInput by remember { mutableStateOf("") }
    val parsedInput = UserCardSettingsEditor.parseTimeoutPreset(timeoutInput)
    val invalidInput = timeoutInput.isNotBlank() && parsedInput == null
    val canAdd = UserCardSettingsEditor.canAddTimeoutPreset(normalized, timeoutInput)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_user_card_timeout_presets),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(Res.string.settings_user_card_timeout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        normalized.userCardTimeoutPresetsSeconds.forEach { seconds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = UserCardSettingsEditor.formatTimeoutPreset(seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onPreferencesChange(
                            UserCardSettingsEditor.removeTimeoutPreset(normalized, seconds),
                        )
                    },
                    enabled = normalized.userCardTimeoutPresetsSeconds.size > 1,
                ) {
                    Text(stringResource(Res.string.settings_user_card_timeout_remove))
                }
            }
        }

        OutlinedTextField(
            value = timeoutInput,
            onValueChange = { value ->
                timeoutInput = value.take(UserCardSettingsEditor.MAX_TIMEOUT_PRESET_INPUT_LENGTH)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = invalidInput,
            label = { Text(stringResource(Res.string.settings_user_card_timeout_input)) },
            supportingText = if (invalidInput) {
                { Text(stringResource(Res.string.settings_user_card_timeout_invalid)) }
            } else {
                null
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    val updated = UserCardSettingsEditor.addTimeoutPreset(normalized, timeoutInput)
                    if (updated != normalized) {
                        onPreferencesChange(updated)
                        timeoutInput = ""
                    }
                },
                enabled = canAdd,
            ) {
                Text(stringResource(Res.string.settings_user_card_timeout_add))
            }
            TextButton(
                onClick = {
                    onPreferencesChange(UserCardSettingsEditor.resetTimeoutPresets(normalized))
                },
                enabled = normalized.userCardTimeoutPresetsSeconds != SharedAppPreferences.DEFAULT_TIMEOUT_PRESETS,
            ) {
                Text(stringResource(Res.string.settings_user_card_timeout_reset))
            }
        }

        Text(
            text = stringResource(Res.string.settings_user_card_action_order),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
        val actionIds = UserCardSettingsEditor.visibleModerationActionIds(normalized)
        actionIds.forEachIndexed { index, actionId ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = userCardModerationActionLabel(actionId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onPreferencesChange(
                            UserCardSettingsEditor.moveModerationAction(normalized, actionId, -1),
                        )
                    },
                    enabled = index > 0,
                ) {
                    Text("↑")
                }
                TextButton(
                    onClick = {
                        onPreferencesChange(
                            UserCardSettingsEditor.moveModerationAction(normalized, actionId, 1),
                        )
                    },
                    enabled = index < actionIds.lastIndex,
                ) {
                    Text("↓")
                }
            }
        }
    }
}

@Composable
private fun userCardModerationActionLabel(actionId: String): String = when (actionId) {
    UserCardModerationLayout.WARN -> stringResource(Res.string.settings_user_card_action_warn)
    UserCardModerationLayout.BAN -> stringResource(Res.string.settings_user_card_action_ban)
    UserCardModerationLayout.UNBAN -> stringResource(Res.string.settings_user_card_action_unban)
    else -> UserCardSettingsEditor.timeoutSeconds(actionId)?.let { seconds ->
        stringResource(
            Res.string.settings_user_card_action_timeout,
            UserCardSettingsEditor.formatTimeoutPreset(seconds),
        )
    } ?: actionId
}
