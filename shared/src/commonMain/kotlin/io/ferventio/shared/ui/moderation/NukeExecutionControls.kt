package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeExecutionPlanResult
import io.ferventio.app.domain.NukeExecutionPlanner
import io.ferventio.app.domain.NukePreview
import io.ferventio.app.domain.NukePreviewConfig
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.nuke_execute
import io.ferventio.shared.generated.resources.nuke_execute_auth_required
import io.ferventio.shared.generated.resources.nuke_execute_cancel
import io.ferventio.shared.generated.resources.nuke_execute_confirm
import io.ferventio.shared.generated.resources.nuke_execute_confirm_body
import io.ferventio.shared.generated.resources.nuke_execute_confirm_title
import io.ferventio.shared.generated.resources.nuke_execute_failed
import io.ferventio.shared.generated.resources.nuke_execute_result
import io.ferventio.shared.generated.resources.nuke_execute_running
import io.ferventio.shared.generated.resources.nuke_execute_stopped_early
import io.ferventio.shared.generated.resources.nuke_execute_too_many
import io.ferventio.shared.moderation.NukeExecutionCoordinator
import io.ferventio.shared.moderation.NukeExecutionResult
import io.ferventio.shared.moderation.NukeModerationAction
import io.ferventio.shared.moderation.shouldStopNukeExecution
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NukeExecutionControls(
    channelId: String,
    config: NukePreviewConfig,
    preview: NukePreview,
    previewedAtMillis: Long,
    onExecutionInFlightChanged: (Boolean) -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val scope = rememberCoroutineScope()
    var pendingPlan by remember(channelId, config, previewedAtMillis) {
        mutableStateOf<NukeExecutionPlan?>(null)
    }
    var executing by remember(channelId) { mutableStateOf(false) }
    var result by remember(channelId) { mutableStateOf<NukeExecutionResult?>(null) }
    var failureMessage by remember(channelId) { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        if (!executing) {
            result = null
            failureMessage = null
        }
    }

    val authenticationAvailable = runtime.authentication.state.authentication != null
    val tooManyTargets = preview.matchedUserCount > MAX_NUKE_TARGET_USERS
    val authenticationUnavailableMessage = stringResource(Res.string.nuke_execute_auth_required)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            tooManyTargets -> Text(
                text = stringResource(Res.string.nuke_execute_too_many, MAX_NUKE_TARGET_USERS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            !authenticationAvailable -> Text(
                text = authenticationUnavailableMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                failureMessage = null
                result = null
                when (
                    val frozen = NukeExecutionPlanner.freeze(
                        config = config,
                        preview = preview,
                        previewedAtMillis = previewedAtMillis,
                    )
                ) {
                    is NukeExecutionPlanResult.Success -> pendingPlan = frozen.plan
                    is NukeExecutionPlanResult.Error -> failureMessage = frozen.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !executing &&
                authenticationAvailable &&
                preview.matchedUserCount in 1..MAX_NUKE_TARGET_USERS,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(
                if (executing) {
                    stringResource(Res.string.nuke_execute_running)
                } else {
                    stringResource(Res.string.nuke_execute)
                },
            )
        }

        result?.let { executionResult ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            Res.string.nuke_execute_result,
                            executionResult.attemptedUsers,
                            executionResult.succeededUsers,
                            executionResult.failedUsers,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (executionResult.stoppedEarly) {
                        Text(
                            text = stringResource(Res.string.nuke_execute_stopped_early),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    executionResult.failures.firstOrNull()?.let { firstFailure ->
                        Text(
                            text = firstFailure.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        failureMessage?.let { message ->
            Text(
                text = stringResource(Res.string.nuke_execute_failed, message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = {
                if (!executing) pendingPlan = null
            },
            title = { Text(stringResource(Res.string.nuke_execute_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.nuke_execute_confirm_body,
                        plan.targetUserCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !executing,
                    onClick = {
                        val approvedPlan = pendingPlan ?: return@TextButton
                        pendingPlan = null
                        executing = true
                        onExecutionInFlightChanged(true)
                        result = null
                        failureMessage = null
                        scope.launch {
                            try {
                                val authentication = runtime.authentication.state.authentication
                                    ?: error(authenticationUnavailableMessage)
                                val coordinator = NukeExecutionCoordinator(
                                    moderationAction = NukeModerationAction { user, durationSeconds, reason ->
                                        runtime.moderation.timeoutUser(
                                            authentication = authentication,
                                            broadcasterId = channelId,
                                            targetUserId = user.userId,
                                            durationSeconds = durationSeconds,
                                            reason = reason,
                                        )
                                    },
                                    shouldStopAfterFailure = { error ->
                                        error.shouldStopNukeExecution()
                                    },
                                )
                                result = coordinator.execute(approvedPlan)
                            } catch (error: Exception) {
                                failureMessage = error.message
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?: "Unknown mass moderation error"
                            } finally {
                                executing = false
                                onExecutionInFlightChanged(false)
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.nuke_execute_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !executing,
                    onClick = { pendingPlan = null },
                ) {
                    Text(stringResource(Res.string.nuke_execute_cancel))
                }
            },
        )
    }
}

private const val MAX_NUKE_TARGET_USERS = 100
