package io.ferventio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.R
import io.ferventio.app.application.ChannelPointsChannelState
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.twitch.TwitchChannelPointsReward

private data class ChannelPointsUiStrings(
    val title: String,
    val balance: String,
    val balanceUnknown: String,
    val refresh: String,
    val empty: String,
    val unavailable: String,
    val notEnough: String,
    val redeem: String,
    val cancel: String,
    val confirmTitle: String,
    val confirmBody: String,
    val inputLabel: String,
    val inputRequired: String,
    val success: String,
    val retry: String,
    val errorNotEnough: String,
    val errorDisabled: String,
    val errorStreamOffline: String,
    val errorCooldown: String,
    val errorLimit: String,
    val errorBanned: String,
    val errorForbidden: String,
    val errorGeneric: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelPointsSheet(
    state: ChannelPointsChannelState?,
    appLanguage: AppLanguage,
    onRefresh: () -> Unit,
    onRedeem: (TwitchChannelPointsReward, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = rememberAppResourceStrings(appLanguage)
    val strings = ChannelPointsUiStrings(
        title = resources.string(R.string.ferventio_channel_points_title),
        balance = resources.string(R.string.ferventio_channel_points_balance),
        balanceUnknown = resources.string(R.string.ferventio_channel_points_balance_unknown),
        refresh = resources.string(R.string.ferventio_channel_points_refresh),
        empty = resources.string(R.string.ferventio_channel_points_empty),
        unavailable = resources.string(R.string.ferventio_channel_points_unavailable),
        notEnough = resources.string(R.string.ferventio_channel_points_not_enough),
        redeem = resources.string(R.string.ferventio_channel_points_redeem),
        cancel = resources.string(R.string.ferventio_channel_points_cancel),
        confirmTitle = resources.string(R.string.ferventio_channel_points_confirm_title),
        confirmBody = resources.string(R.string.ferventio_channel_points_confirm_body),
        inputLabel = resources.string(R.string.ferventio_channel_points_input_label),
        inputRequired = resources.string(R.string.ferventio_channel_points_input_required),
        success = resources.string(R.string.ferventio_channel_points_success),
        retry = resources.string(R.string.ferventio_channel_points_retry),
        errorNotEnough = resources.string(R.string.ferventio_channel_points_error_not_enough),
        errorDisabled = resources.string(R.string.ferventio_channel_points_error_disabled),
        errorStreamOffline = resources.string(R.string.ferventio_channel_points_error_stream_offline),
        errorCooldown = resources.string(R.string.ferventio_channel_points_error_cooldown),
        errorLimit = resources.string(R.string.ferventio_channel_points_error_limit),
        errorBanned = resources.string(R.string.ferventio_channel_points_error_banned),
        errorForbidden = resources.string(R.string.ferventio_channel_points_error_forbidden),
        errorGeneric = resources.string(R.string.ferventio_channel_points_error_generic),
    )
    var pendingReward by remember { mutableStateOf<TwitchChannelPointsReward?>(null) }
    var rewardInput by remember(pendingReward?.id) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        LocalizedText(
                            strings.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        LocalizedText(
                            text = if (state?.balance != null) {
                                "${strings.balance}: ${state.balance}"
                            } else {
                                "${strings.balance}: ${strings.balanceUnknown}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = state?.loading != true) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.refresh)
                    }
                }
            }

            if (state?.loading == true) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }

            state?.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                item(key = "error") {
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
                                channelPointsErrorText(error, strings),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            OutlinedButton(onClick = onRefresh) {
                                LocalizedText(strings.retry)
                            }
                        }
                    }
                }
            }

            if (state?.lastRedemptionId != null && state.errorMessage == null) {
                item(key = "success") {
                    LocalizedText(
                        strings.success,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val rewards = state?.rewards.orEmpty()
            if (state?.loading != true && rewards.isEmpty() && state?.errorMessage == null) {
                item(key = "empty") {
                    LocalizedText(
                        strings.empty,
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = rewards,
                key = TwitchChannelPointsReward::id,
            ) { reward ->
                val affordable = state?.balance?.let { balance -> balance >= reward.cost } ?: true
                val enabled = reward.enabled && affordable && state?.redeemingRewardId == null
                ChannelPointsRewardRow(
                    reward = reward,
                    enabled = enabled,
                    status = when {
                        !reward.enabled -> strings.unavailable
                        !affordable -> strings.notEnough
                        else -> null
                    },
                    redeeming = state?.redeemingRewardId == reward.id,
                    onClick = { pendingReward = reward },
                )
            }
        }
    }

    pendingReward?.let { reward ->
        val canSubmit = !reward.userInputRequired || rewardInput.isNotBlank()
        AlertDialog(
            onDismissRequest = { pendingReward = null },
            title = { LocalizedText(strings.confirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VerbatimText(
                        reward.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    LocalizedText("${strings.confirmBody} ${reward.cost}")
                    if (reward.prompt.isNotBlank()) {
                        VerbatimText(
                            reward.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reward.userInputRequired) {
                        OutlinedTextField(
                            value = rewardInput,
                            onValueChange = { rewardInput = it.take(500) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { LocalizedText(strings.inputLabel) },
                            supportingText = {
                                if (rewardInput.isBlank()) LocalizedText(strings.inputRequired)
                            },
                            minLines = 2,
                            maxLines = 5,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        pendingReward = null
                        onRedeem(reward, rewardInput.takeIf(String::isNotBlank))
                    },
                ) {
                    LocalizedText(strings.redeem)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReward = null }) {
                    LocalizedText(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun ChannelPointsRewardRow(
    reward: TwitchChannelPointsReward,
    enabled: Boolean,
    status: String?,
    redeeming: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                VerbatimText(
                    reward.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reward.prompt.isNotBlank()) {
                    VerbatimText(
                        reward.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status?.let {
                    LocalizedText(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (redeeming) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            } else {
                VerbatimText(
                    reward.cost.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun channelPointsErrorText(error: String, strings: ChannelPointsUiStrings): String {
    val code = error.substringAfter("Channel Points redemption failed:", "").trim()
    return when (code) {
        "NOT_ENOUGH_POINTS" -> strings.errorNotEnough
        "DISABLED", "CHANNEL_SETTINGS" -> strings.errorDisabled
        "STREAM_NOT_LIVE" -> strings.errorStreamOffline
        "GLOBAL_COOLDOWN" -> strings.errorCooldown
        "MAX_PER_STREAM", "MAX_PER_USER_PER_STREAM" -> strings.errorLimit
        "USER_BANNED" -> strings.errorBanned
        "FORBIDDEN" -> strings.errorForbidden
        else -> if (error.isBlank()) strings.errorGeneric else "${strings.errorGeneric}: $error"
    }
}
