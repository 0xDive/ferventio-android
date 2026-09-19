package io.ferventio.shared.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_history
import io.ferventio.shared.generated.resources.settings_history_clear
import io.ferventio.shared.generated.resources.settings_history_clear_cancel
import io.ferventio.shared.generated.resources.settings_history_clear_confirm
import io.ferventio.shared.generated.resources.settings_history_clear_confirm_body
import io.ferventio.shared.generated.resources.settings_history_clear_confirm_title
import io.ferventio.shared.generated.resources.settings_history_clear_failed
import io.ferventio.shared.generated.resources.settings_history_cleared
import io.ferventio.shared.generated.resources.settings_history_clearing
import io.ferventio.shared.generated.resources.settings_history_device_only
import io.ferventio.shared.generated.resources.settings_history_limit
import io.ferventio.shared.generated.resources.settings_history_max_size
import io.ferventio.shared.generated.resources.settings_history_max_size_unlimited
import io.ferventio.shared.generated.resources.settings_history_retention
import io.ferventio.shared.generated.resources.settings_history_retention_unlimited
import io.ferventio.shared.generated.resources.settings_local_history
import io.ferventio.shared.generated.resources.settings_recent_messages
import io.ferventio.shared.generated.resources.settings_recent_messages_privacy_note
import io.ferventio.shared.generated.resources.settings_recent_messages_service_info
import io.ferventio.shared.generated.resources.settings_recent_messages_summary
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.HistorySettingsPresets
import io.ferventio.shared.settings.SharedAppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val RECENT_MESSAGES_SERVICE_URL = "https://recent-messages.robotty.de/"

@Composable
internal fun FerventioHistorySettingsPage(
    preferences: SharedAppPreferences,
    update: ((SharedAppPreferences) -> SharedAppPreferences) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showClearConfirmation by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var clearSucceeded by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }

    FerventioSettingsSection(
        title = stringResource(Res.string.settings_history),
    ) {
        HistorySwitchRow(
            label = stringResource(Res.string.settings_recent_messages),
            checked = preferences.recentMessagesEnabled,
            onCheckedChange = { value -> update { it.copy(recentMessagesEnabled = value) } },
        )
        Text(
            text = stringResource(Res.string.settings_recent_messages_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.settings_recent_messages_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(
            onClick = { uriHandler.openUri(RECENT_MESSAGES_SERVICE_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.settings_recent_messages_service_info))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        HistorySwitchRow(
            label = stringResource(Res.string.settings_local_history),
            checked = preferences.localHistoryEnabled,
            onCheckedChange = { value -> update { it.copy(localHistoryEnabled = value) } },
        )
        Text(
            text = stringResource(Res.string.settings_history_device_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preferences.localHistoryEnabled) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HistorySettingsPresets.messageLimits.forEach { value ->
                    HistoryPresetRow(
                        label = stringResource(Res.string.settings_history_limit, value),
                        selected = preferences.localHistoryLimit == value,
                        onClick = { update { it.copy(localHistoryLimit = value) } },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HistorySettingsPresets.retentionDays.forEach { days ->
                    HistoryPresetRow(
                        label = if (days == 0) {
                            stringResource(Res.string.settings_history_retention_unlimited)
                        } else {
                            stringResource(Res.string.settings_history_retention, days)
                        },
                        selected = preferences.localHistoryRetentionDays == days,
                        onClick = { update { it.copy(localHistoryRetentionDays = days) } },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HistorySettingsPresets.maxSizeMb.forEach { sizeMb ->
                    HistoryPresetRow(
                        label = if (sizeMb == 0) {
                            stringResource(Res.string.settings_history_max_size_unlimited)
                        } else {
                            stringResource(Res.string.settings_history_max_size, sizeMb)
                        },
                        selected = preferences.localHistoryMaxSizeMb == sizeMb,
                        onClick = { update { it.copy(localHistoryMaxSizeMb = sizeMb) } },
                    )
                }
            }
        }

        if (runtime.history != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            when {
                isClearing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(Res.string.settings_history_clearing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                clearSucceeded -> Text(
                    text = stringResource(Res.string.settings_history_cleared),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                clearFailed -> Text(
                    text = stringResource(Res.string.settings_history_clear_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                enabled = !isClearing,
                onClick = {
                    clearSucceeded = false
                    clearFailed = false
                    showClearConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.settings_history_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearConfirmation = false },
            title = { Text(stringResource(Res.string.settings_history_clear_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    enabled = !isClearing,
                    onClick = {
                        showClearConfirmation = false
                        isClearing = true
                        clearSucceeded = false
                        clearFailed = false
                        scope.launch {
                            try {
                                runtime.clearLocalHistory()
                                clearSucceeded = true
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                clearFailed = true
                            } finally {
                                isClearing = false
                            }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.settings_history_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isClearing,
                    onClick = { showClearConfirmation = false },
                ) {
                    Text(stringResource(Res.string.settings_history_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistorySwitchRow(
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
private fun HistoryPresetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
