package io.ferventio.shared.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_user_card_action_timeout
import io.ferventio.shared.generated.resources.user_card_ban
import io.ferventio.shared.generated.resources.user_card_ban_done
import io.ferventio.shared.generated.resources.user_card_cancel
import io.ferventio.shared.generated.resources.user_card_confirm
import io.ferventio.shared.generated.resources.user_card_confirm_ban
import io.ferventio.shared.generated.resources.user_card_confirm_delete
import io.ferventio.shared.generated.resources.user_card_confirm_title
import io.ferventio.shared.generated.resources.user_card_delete_done
import io.ferventio.shared.generated.resources.user_card_delete_message
import io.ferventio.shared.generated.resources.user_card_moderation_actions
import io.ferventio.shared.generated.resources.user_card_moderation_failed
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.UserCardSettingsEditor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private sealed interface UserCardPendingModerationAction {
    data class Timeout(val durationSeconds: Int) : UserCardPendingModerationAction
    data object Ban : UserCardPendingModerationAction
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

    val configuredActions = UserCardModerationActionPolicy.visibleActions(runtime.settings.preferences)
    val scope = rememberCoroutineScope()
    val targetLabel = data.user.displayName
        .trim()
        .takeIf(String::isNotEmpty)
        ?: data.user.login.trim().takeIf(String::isNotEmpty)
        ?: data.user.id
    var pendingAction by remember(data.channelId, data.user.id, data.sourceMessageId) {
        mutableStateOf<UserCardPendingModerationAction?>(null)
    }
    var mutationInFlight by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var feedback by remember(data.channelId, data.user.id) {
        mutableStateOf<UserCardModerationFeedback?>(null)
    }

    val banSuccess = stringResource(Res.string.user_card_ban_done, targetLabel)
    val deleteSuccess = stringResource(Res.string.user_card_delete_done)
    val pendingTimeoutLabel = when (val action = pendingAction) {
        is UserCardPendingModerationAction.Timeout -> userCardTimeoutActionLabel(action.durationSeconds)
        else -> null
    }
    val pendingSuccess = when (pendingAction) {
        is UserCardPendingModerationAction.Timeout -> "$targetLabel · ${pendingTimeoutLabel.orEmpty()}"
        UserCardPendingModerationAction.Ban -> banSuccess
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

    pendingAction?.let { action ->
        val confirmation = when (action) {
            is UserCardPendingModerationAction.Timeout ->
                "$targetLabel · ${pendingTimeoutLabel.orEmpty()}"
            UserCardPendingModerationAction.Ban ->
                stringResource(Res.string.user_card_confirm_ban, targetLabel)
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
