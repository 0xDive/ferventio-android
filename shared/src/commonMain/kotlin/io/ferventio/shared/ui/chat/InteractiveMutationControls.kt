package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import io.ferventio.app.domain.InteractiveOverlayDraftValidator
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.interactive_cancel
import io.ferventio.shared.generated.resources.interactive_choice_one
import io.ferventio.shared.generated.resources.interactive_choice_two
import io.ferventio.shared.generated.resources.interactive_confirm_action
import io.ferventio.shared.generated.resources.interactive_confirm_cancel_prediction
import io.ferventio.shared.generated.resources.interactive_confirm_end_poll
import io.ferventio.shared.generated.resources.interactive_confirm_lock_prediction
import io.ferventio.shared.generated.resources.interactive_confirm_resolve_prediction
import io.ferventio.shared.generated.resources.interactive_confirm_title
import io.ferventio.shared.generated.resources.interactive_create
import io.ferventio.shared.generated.resources.interactive_create_poll
import io.ferventio.shared.generated.resources.interactive_create_poll_title
import io.ferventio.shared.generated.resources.interactive_create_prediction
import io.ferventio.shared.generated.resources.interactive_create_prediction_title
import io.ferventio.shared.generated.resources.interactive_duration
import io.ferventio.shared.generated.resources.interactive_mutation_failed
import io.ferventio.shared.generated.resources.interactive_mutation_in_flight
import io.ferventio.shared.generated.resources.interactive_outcome_one
import io.ferventio.shared.generated.resources.interactive_outcome_two
import io.ferventio.shared.generated.resources.interactive_poll_end
import io.ferventio.shared.generated.resources.interactive_prediction_cancel
import io.ferventio.shared.generated.resources.interactive_prediction_lock
import io.ferventio.shared.generated.resources.interactive_prediction_resolve
import io.ferventio.shared.generated.resources.interactive_seconds
import io.ferventio.shared.generated.resources.interactive_title_label
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val POLL_SCOPE = "channel:manage:polls"
private const val PREDICTION_SCOPE = "channel:manage:predictions"
private val POLL_DURATION_PRESETS = listOf(30, 60, 120, 300)
private val PREDICTION_WINDOW_PRESETS = listOf(60, 120, 300, 600)

private sealed interface PendingInteractiveAction {
    data class EndPoll(val pollId: String) : PendingInteractiveAction
    data class LockPrediction(val predictionId: String) : PendingInteractiveAction
    data class CancelPrediction(val predictionId: String) : PendingInteractiveAction
    data class ResolvePrediction(
        val predictionId: String,
        val outcomeId: String,
        val outcomeTitle: String,
    ) : PendingInteractiveAction
}

@Composable
internal fun InteractiveMutationControls(
    channelId: String,
    poll: PollOverlay?,
    prediction: PredictionOverlay?,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val session = authentication?.accessLease?.session
    val ownsChannel = session?.userId == channelId
    val canManagePolls = ownsChannel && session?.scopes?.contains(POLL_SCOPE) == true
    val canManagePredictions = ownsChannel &&
        session?.scopes?.contains(PREDICTION_SCOPE) == true
    if (!canManagePolls && !canManagePredictions) return

    val mutation = runtime.chat.interactiveState.mutationsByChannel[channelId]
    val mutationInFlight = mutation?.inFlight == true
    val scope = rememberCoroutineScope()
    var showPollDialog by remember(channelId) { mutableStateOf(false) }
    var showPredictionDialog by remember(channelId) { mutableStateOf(false) }
    var pendingAction by remember(channelId) {
        mutableStateOf<PendingInteractiveAction?>(null)
    }
    var errorMessage by remember(channelId) { mutableStateOf<String?>(null) }

    fun launchMutation(block: suspend () -> Unit) {
        errorMessage = null
        scope.launch {
            try {
                block()
            } catch (error: Exception) {
                errorMessage = error.message
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: "Unknown Twitch interactive error"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canManagePolls && poll?.isActive != true) {
                OutlinedButton(
                    onClick = { showPollDialog = true },
                    enabled = !mutationInFlight,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.interactive_create_poll))
                }
            }
            val predictionOpen = prediction?.isActive == true || prediction?.isLocked == true
            if (canManagePredictions && !predictionOpen) {
                OutlinedButton(
                    onClick = { showPredictionDialog = true },
                    enabled = !mutationInFlight,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.interactive_create_prediction))
                }
            }
        }

        if (canManagePolls && poll?.status == PollStatus.ACTIVE) {
            OutlinedButton(
                onClick = { pendingAction = PendingInteractiveAction.EndPoll(poll.id) },
                enabled = !mutationInFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.interactive_poll_end))
            }
        }

        if (canManagePredictions && prediction != null) {
            when (prediction.status) {
                PredictionStatus.ACTIVE -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingAction = PendingInteractiveAction.LockPrediction(prediction.id)
                        },
                        enabled = !mutationInFlight,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.interactive_prediction_lock))
                    }
                    OutlinedButton(
                        onClick = {
                            pendingAction = PendingInteractiveAction.CancelPrediction(prediction.id)
                        },
                        enabled = !mutationInFlight,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.interactive_prediction_cancel))
                    }
                }

                PredictionStatus.LOCKED -> {
                    OutlinedButton(
                        onClick = {
                            pendingAction = PendingInteractiveAction.CancelPrediction(prediction.id)
                        },
                        enabled = !mutationInFlight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.interactive_prediction_cancel))
                    }
                    prediction.outcomes.forEach { outcome ->
                        Button(
                            onClick = {
                                pendingAction = PendingInteractiveAction.ResolvePrediction(
                                    predictionId = prediction.id,
                                    outcomeId = outcome.id,
                                    outcomeTitle = outcome.title,
                                )
                            },
                            enabled = !mutationInFlight,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${stringResource(Res.string.interactive_prediction_resolve)}: ${outcome.title}",
                            )
                        }
                    }
                }

                PredictionStatus.RESOLVED,
                PredictionStatus.CANCELED,
                PredictionStatus.UNKNOWN -> Unit
            }
        }

        if (mutationInFlight) {
            Text(
                text = stringResource(Res.string.interactive_mutation_in_flight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        errorMessage?.let { error ->
            Text(
                text = stringResource(Res.string.interactive_mutation_failed, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPollDialog && canManagePolls) {
        CreatePollDialog(
            busy = mutationInFlight,
            onDismiss = { if (!mutationInFlight) showPollDialog = false },
            onCreate = { draft ->
                authentication?.let { auth ->
                    launchMutation {
                        runtime.interactive.createPoll(auth, channelId, draft)
                        showPollDialog = false
                    }
                }
            },
        )
    }

    if (showPredictionDialog && canManagePredictions) {
        CreatePredictionDialog(
            busy = mutationInFlight,
            onDismiss = { if (!mutationInFlight) showPredictionDialog = false },
            onCreate = { draft ->
                authentication?.let { auth ->
                    launchMutation {
                        runtime.interactive.createPrediction(auth, channelId, draft)
                        showPredictionDialog = false
                    }
                }
            },
        )
    }

    pendingAction?.let { action ->
        val confirmation = when (action) {
            is PendingInteractiveAction.EndPoll ->
                stringResource(Res.string.interactive_confirm_end_poll)
            is PendingInteractiveAction.LockPrediction ->
                stringResource(Res.string.interactive_confirm_lock_prediction)
            is PendingInteractiveAction.CancelPrediction ->
                stringResource(Res.string.interactive_confirm_cancel_prediction)
            is PendingInteractiveAction.ResolvePrediction ->
                stringResource(
                    Res.string.interactive_confirm_resolve_prediction,
                    action.outcomeTitle,
                )
        }
        AlertDialog(
            onDismissRequest = { if (!mutationInFlight) pendingAction = null },
            title = { Text(stringResource(Res.string.interactive_confirm_title)) },
            text = { Text(confirmation) },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = {
                        val approved = pendingAction ?: return@TextButton
                        val auth = authentication ?: return@TextButton
                        pendingAction = null
                        launchMutation {
                            when (approved) {
                                is PendingInteractiveAction.EndPoll -> runtime.interactive.endPoll(
                                    authentication = auth,
                                    broadcasterId = channelId,
                                    pollId = approved.pollId,
                                    status = PollStatus.TERMINATED,
                                )
                                is PendingInteractiveAction.LockPrediction ->
                                    runtime.interactive.endPrediction(
                                        authentication = auth,
                                        broadcasterId = channelId,
                                        predictionId = approved.predictionId,
                                        status = PredictionStatus.LOCKED,
                                    )
                                is PendingInteractiveAction.CancelPrediction ->
                                    runtime.interactive.endPrediction(
                                        authentication = auth,
                                        broadcasterId = channelId,
                                        predictionId = approved.predictionId,
                                        status = PredictionStatus.CANCELED,
                                    )
                                is PendingInteractiveAction.ResolvePrediction ->
                                    runtime.interactive.endPrediction(
                                        authentication = auth,
                                        broadcasterId = channelId,
                                        predictionId = approved.predictionId,
                                        status = PredictionStatus.RESOLVED,
                                        winningOutcomeId = approved.outcomeId,
                                    )
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.interactive_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = { pendingAction = null },
                ) {
                    Text(stringResource(Res.string.interactive_cancel))
                }
            },
        )
    }
}

@Composable
private fun CreatePollDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (PollDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var firstChoice by remember { mutableStateOf("") }
    var secondChoice by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(60) }
    val draft = PollDraft(
        title = title.trim(),
        choices = listOf(firstChoice.trim(), secondChoice.trim()),
        durationSeconds = duration,
    )
    val valid = InteractiveOverlayDraftValidator.validatePoll(draft).isEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.interactive_create_poll_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_title_label)) },
                )
                OutlinedTextField(
                    value = firstChoice,
                    onValueChange = { firstChoice = it.take(25) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_choice_one)) },
                )
                OutlinedTextField(
                    value = secondChoice,
                    onValueChange = { secondChoice = it.take(25) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_choice_two)) },
                )
                DurationPresets(
                    selected = duration,
                    values = POLL_DURATION_PRESETS,
                    enabled = !busy,
                    onSelected = { duration = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(draft) },
                enabled = valid && !busy,
            ) {
                Text(stringResource(Res.string.interactive_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.interactive_cancel))
            }
        },
    )
}

@Composable
private fun CreatePredictionDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (PredictionDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var firstOutcome by remember { mutableStateOf("") }
    var secondOutcome by remember { mutableStateOf("") }
    var windowSeconds by remember { mutableStateOf(120) }
    val draft = PredictionDraft(
        title = title.trim(),
        outcomes = listOf(firstOutcome.trim(), secondOutcome.trim()),
        predictionWindowSeconds = windowSeconds,
    )
    val valid = InteractiveOverlayDraftValidator.validatePrediction(draft).isEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.interactive_create_prediction_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(45) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_title_label)) },
                )
                OutlinedTextField(
                    value = firstOutcome,
                    onValueChange = { firstOutcome = it.take(25) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_outcome_one)) },
                )
                OutlinedTextField(
                    value = secondOutcome,
                    onValueChange = { secondOutcome = it.take(25) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.interactive_outcome_two)) },
                )
                DurationPresets(
                    selected = windowSeconds,
                    values = PREDICTION_WINDOW_PRESETS,
                    enabled = !busy,
                    onSelected = { windowSeconds = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(draft) },
                enabled = valid && !busy,
            ) {
                Text(stringResource(Res.string.interactive_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.interactive_cancel))
            }
        },
    )
}

@Composable
private fun DurationPresets(
    selected: Int,
    values: List<Int>,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.interactive_duration),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { seconds ->
                FilterChip(
                    selected = selected == seconds,
                    onClick = { onSelected(seconds) },
                    enabled = enabled,
                    label = { Text(stringResource(Res.string.interactive_seconds, seconds)) },
                )
            }
        }
    }
}
