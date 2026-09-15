package io.ferventio.shared.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.UserCardBanState
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_user_card_action_timeout
import io.ferventio.shared.generated.resources.settings_user_card_action_unban
import io.ferventio.shared.generated.resources.settings_user_card_action_warn
import io.ferventio.shared.generated.resources.user_card_ban
import io.ferventio.shared.generated.resources.user_card_ban_done
import io.ferventio.shared.generated.resources.user_card_cancel
import io.ferventio.shared.generated.resources.user_card_confirm
import io.ferventio.shared.generated.resources.user_card_confirm_ban
import io.ferventio.shared.generated.resources.user_card_confirm_delete
import io.ferventio.shared.generated.resources.user_card_confirm_title
import io.ferventio.shared.generated.resources.user_card_confirm_unban
import io.ferventio.shared.generated.resources.user_card_delete_done
import io.ferventio.shared.generated.resources.user_card_delete_message
import io.ferventio.shared.generated.resources.user_card_moderation_actions
import io.ferventio.shared.generated.resources.user_card_moderation_failed
import io.ferventio.shared.generated.resources.user_card_unban_done
import io.ferventio.shared.generated.resources.user_card_warn_done
import io.ferventio.shared.generated.resources.user_card_warn_reason
import io.ferventio.shared.generated.resources.user_card_warn_reason_hint
import io.ferventio.shared.generated.resources.user_card_warn_title
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.UserCardSettingsEditor
import io.ferventio.shared.user.TwitchUserCardClient
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private sealed interface UserCardPendingModerationAction {
    data class Timeout(val durationSeconds: Int) : UserCardPendingModerationAction
    data object Ban : UserCardPendingModerationAction
    data object Unban : UserCardPendingModerationAction
    data object DeleteMessage : UserCardPendingModerationAction
}

private data class UserCardModerationFeedback(
    val message: String,
    val isError: Boolean,
)

@Composable
internal fun UserCardModerationActions(
    data: UserCardData,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val availability = userCardModerationAvailability(
        data = data,
        authenticatedUserId = authentication?.accessLease?.session?.userId,
    )
    if (!availability.canModerateUser && !availability.canDeleteSourceMessage) return

    val banStateClient = remember { TwitchUserCardClient() }
    DisposableEffect(banStateClient) {
        onDispose { banStateClient.close() }
    }
    var remotePermanentBan by remember(data.channelId, data.user.id) {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(
        authentication,
        availability.canModerateUser,
        data.channelId,
        data.user.id,
    ) {
        remotePermanentBan = null
        if (authentication != null && availability.canModerateUser) {
            remotePermanentBan = runCatching {
                banStateClient.loadPermanentBanState(
                    authentication = authentication,
                    broadcasterId = data.channelId,
                    targetUserId = data.user.id,
                )
            }.getOrNull()
        }
    }
    val resolvedPermanentBan = remember(remotePermanentBan, data.localActions) {
        UserCardBanState.resolve(
            knownPermanentlyBanned = remotePermanentBan == true,
            localActions = data.localActions,
        )
    }
    var permanentBanOverride by remember(
        data.channelId,
        data.user.id,
        resolvedPermanentBan,
    ) {
        mutableStateOf<Boolean?>(null)
    }
    val isPermanentlyBanned = permanentBanOverride ?: resolvedPermanentBan
    val configuredActions = UserCardModerationActionPolicy.visibleActions(
        preferences = runtime.settings.preferences,
        isPermanentlyBanned = isPermanentlyBanned,
    )
    val scope = rememberCoroutineScope()
    val targetLabel = data.user.displayName
        .trim()
        .takeIf(String::isNotEmpty)
        ?: data.user.login.trim().takeIf(String::isNotEmpty)
        ?: data.user.id
    var pendingAction by remember(data.channelId, data.user.id, data.sourceMessageId) {
        mutableStateOf<UserCardPendingModerationAction?>(null)
    }
    var showWarnDialog by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var warnReason by remember(data.channelId, data.user.id) { mutableStateOf("") }
    var mutationInFlight by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var feedback by remember(data.channelId, data.user.id) {
        mutableStateOf<UserCardModerationFeedback?>(null)
    }

    val banSuccess = stringResource(Res.string.user_card_ban_done, targetLabel)
    val unbanSuccess = stringResource(Res.string.user_card_unban_done, targetLabel)
    val warnSuccess = stringResource(Res.string.user_card_warn_done, targetLabel)
    val deleteSuccess = stringResource(Res.string.user_card_delete_done)
    val pendingTimeoutLabel = when (val action = pendingAction) {
        is UserCardPendingModerationAction.Timeout -> userCardTimeoutActionLabel(action.durationSeconds)
        else -> null
    }
    val pendingSuccess = when (pendingAction) {
        is UserCardPendingModerationAction.Timeout -> "$targetLabel · ${pendingTimeoutLabel.orEmpty()}"
        UserCardPendingModerationAction.Ban -> banSuccess
        UserCardPendingModerationAction.Unban -> unbanSuccess
        UserCardPendingModerationAction.DeleteMessage -> deleteSuccess
        null -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.user_card_moderation_actions),
            style = MaterialTheme.typography.titleSmall,
        )
        if (availability.canModerateUser) {
            configuredActions.forEach { action ->
                when (action) {
                    is UserCardRuntimeModerationAction.Timeout -> {
                        OutlinedButton(
                            onClick = {
                                pendingAction = UserCardPendingModerationAction.Timeout(
                                    action.durationSeconds,
                                )
                            },
                            enabled = !mutationInFlight,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(userCardTimeoutActionLabel(action.durationSeconds))
                        }
                    }
                    UserCardRuntimeModerationAction.Warn -> {
                        OutlinedButton(
                            onClick = {
                                warnReason = ""
                                showWarnDialog = true
                            },
                            enabled = !mutationInFlight,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.settings_user_card_action_warn))
                        }
                    }
                    UserCardRuntimeModerationAction.Ban -> {
                        Button(
                            onClick = { pendingAction = UserCardPendingModerationAction.Ban },
                            enabled = !mutationInFlight,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(Res.string.user_card_ban))
                        }
                    }
                    UserCardRuntimeModerationAction.Unban -> {
                        OutlinedButton(
                            onClick = { pendingAction = UserCardPendingModerationAction.Unban },
                            enabled = !mutationInFlight,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.settings_user_card_action_unban))
                        }
                    }
                }
            }
        }
        if (availability.canDeleteSourceMessage) {
            OutlinedButton(
                onClick = { pendingAction = UserCardPendingModerationAction.DeleteMessage },
                enabled = !mutationInFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.user_card_delete_message))
            }
        }
        feedback?.let { result ->
            Text(
                text = if (result.isError) {
                    stringResource(Res.string.user_card_moderation_failed, result.message)
                } else {
                    result.message
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (result.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }

    if (showWarnDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!mutationInFlight) {
                    showWarnDialog = false
                    warnReason = ""
                }
            },
            title = { Text(stringResource(Res.string.user_card_warn_title, targetLabel)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.user_card_warn_reason_hint))
                    OutlinedTextField(
                        value = warnReason,
                        onValueChange = { value -> warnReason = value.take(MAX_MODERATION_REASON_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.user_card_warn_reason)) },
                        singleLine = false,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight && warnReason.isNotBlank(),
                    onClick = {
                        val reason = warnReason.trim()
                        if (reason.isEmpty()) return@TextButton
                        showWarnDialog = false
                        warnReason = ""
                        mutationInFlight = true
                        feedback = null
                        scope.launch {
                            try {
                                val currentAuthentication =
                                    runtime.authentication.state.authentication
                                        ?: error("Twitch authentication is unavailable")
                                runtime.moderation.warnUser(
                                    authentication = currentAuthentication,
                                    broadcasterId = data.channelId,
                                    targetUserId = data.user.id,
                                    reason = reason,
                                )
                                feedback = UserCardModerationFeedback(
                                    message = warnSuccess,
                                    isError = false,
                                )
                            } catch (error: Exception) {
                                feedback = UserCardModerationFeedback(
                                    message = error.message
                                        ?.trim()
                                        ?.takeIf(String::isNotEmpty)
                                        ?: "Unknown Twitch moderation error",
                                    isError = true,
                                )
                            } finally {
                                mutationInFlight = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.settings_user_card_action_warn))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = {
                        showWarnDialog = false
                        warnReason = ""
                    },
                ) {
                    Text(stringResource(Res.string.user_card_cancel))
                }
            },
        )
    }

    pendingAction?.let { action ->
        val confirmation = when (action) {
            is UserCardPendingModerationAction.Timeout ->
                "$targetLabel · ${pendingTimeoutLabel.orEmpty()}"
            UserCardPendingModerationAction.Ban ->
                stringResource(Res.string.user_card_confirm_ban, targetLabel)
            UserCardPendingModerationAction.Unban ->
                stringResource(Res.string.user_card_confirm_unban, targetLabel)
            UserCardPendingModerationAction.DeleteMessage ->
                stringResource(Res.string.user_card_confirm_delete)
        }
        AlertDialog(
            onDismissRequest = {
                if (!mutationInFlight) pendingAction = null
            },
            title = { Text(stringResource(Res.string.user_card_confirm_title)) },
            text = { Text(confirmation) },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = {
                        val selectedAction = pendingAction ?: return@TextButton
                        val successMessage = pendingSuccess
                        pendingAction = null
                        mutationInFlight = true
                        feedback = null
                        scope.launch {
                            try {
                                val currentAuthentication =
                                    runtime.authentication.state.authentication
                                        ?: error("Twitch authentication is unavailable")
                                when (selectedAction) {
                                    is UserCardPendingModerationAction.Timeout -> {
                                        runtime.moderation.timeoutUser(
                                            authentication = currentAuthentication,
                                            broadcasterId = data.channelId,
                                            targetUserId = data.user.id,
                                            durationSeconds = selectedAction.durationSeconds,
                                        )
                                    }
                                    UserCardPendingModerationAction.Ban -> {
                                        runtime.moderation.banUser(
                                            authentication = currentAuthentication,
                                            broadcasterId = data.channelId,
                                            targetUserId = data.user.id,
                                        )
                                        permanentBanOverride = true
                                    }
                                    UserCardPendingModerationAction.Unban -> {
                                        runtime.moderation.unbanUser(
                                            authentication = currentAuthentication,
                                            broadcasterId = data.channelId,
                                            targetUserId = data.user.id,
                                        )
                                        permanentBanOverride = false
                                    }
                                    UserCardPendingModerationAction.DeleteMessage -> {
                                        val messageId = data.sourceMessageId
                                            ?: error("Selected message is unavailable")
                                        runtime.moderation.deleteChatMessage(
                                            authentication = currentAuthentication,
                                            broadcasterId = data.channelId,
                                            messageId = messageId,
                                        )
                                    }
                                }
                                feedback = UserCardModerationFeedback(
                                    message = successMessage,
                                    isError = false,
                                )
                            } catch (error: Exception) {
                                feedback = UserCardModerationFeedback(
                                    message = error.message
                                        ?.trim()
                                        ?.takeIf(String::isNotEmpty)
                                        ?: "Unknown Twitch moderation error",
                                    isError = true,
                                )
                            } finally {
                                mutationInFlight = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.user_card_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = { pendingAction = null },
                ) {
                    Text(stringResource(Res.string.user_card_cancel))
                }
            },
        )
    }
}

@Composable
private fun userCardTimeoutActionLabel(durationSeconds: Int): String =
    stringResource(
        Res.string.settings_user_card_action_timeout,
        UserCardSettingsEditor.formatTimeoutPreset(durationSeconds),
    )

private const val MAX_MODERATION_REASON_LENGTH = 500
