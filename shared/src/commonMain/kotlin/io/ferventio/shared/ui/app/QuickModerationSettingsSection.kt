package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.quick_moderation_confirm_actions
import io.ferventio.shared.generated.resources.quick_moderation_settings_title
import io.ferventio.shared.generated.resources.quick_moderation_show_ban
import io.ferventio.shared.generated.resources.quick_moderation_show_delete
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuickModerationSettingsSection() {
    val localPreferences = LocalFerventioRuntimeState.current.localUiPreferences

    Text(
        text = stringResource(Res.string.quick_moderation_settings_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp),
    )
    LocalPreferenceSwitchRow(
        label = stringResource(Res.string.quick_moderation_show_ban),
        checked = localPreferences.preferences.showQuickBan,
        onCheckedChange = localPreferences::setShowQuickBan,
    )
    LocalPreferenceSwitchRow(
        label = stringResource(Res.string.quick_moderation_show_delete),
        checked = localPreferences.preferences.showQuickDelete,
        onCheckedChange = localPreferences::setShowQuickDelete,
    )
    LocalPreferenceSwitchRow(
        label = stringResource(Res.string.quick_moderation_confirm_actions),
        checked = localPreferences.preferences.confirmModerationActions,
        onCheckedChange = localPreferences::setConfirmModerationActions,
    )
}

@Composable
private fun LocalPreferenceSwitchRow(
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
