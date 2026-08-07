package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.InteractiveMutationStatus
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus

internal data class InteractiveOverlayUiStrings(
    val pollLabel: String,
    val predictionLabel: String,
    val votesLabel: String,
    val usersLabel: String,
    val pointsLabel: String,
    val endPoll: String,
    val archivePoll: String,
    val lockPrediction: String,
    val cancelPrediction: String,
    val resolvePrediction: String,
    val working: String,
    val actionFailed: String,
)

private enum class InteractiveManagementAction {
    END_POLL,
    ARCHIVE_POLL,
    LOCK_PREDICTION,
    CANCEL_PREDICTION,
    RESOLVE_PREDICTION,
}

private data class PendingInteractiveManagementAction(
    val action: InteractiveManagementAction,
    val outcomeId: String? = null,
)

@Composable
internal fun InteractiveChatOverlayStack(
    poll: PollOverlay?,
    prediction: PredictionOverlay?,
    mutation: InteractiveMutationStatus?,
    strings: InteractiveOverlayUiStrings,
    creationStrings: InteractiveCreationUiStrings,
    canManagePoll: Boolean,
    canManagePrediction: Boolean,
    onCreatePoll: (PollDraft) -> Unit = {},
    onCreatePrediction: (PredictionDraft) -> Unit = {},
    onEndPoll: () -> Unit = {},
    onArchivePoll: () -> Unit = {},
    onLockPrediction: () -> Unit = {},
    onCancelPrediction: () -> Unit = {},
    onResolvePrediction: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mutationInFlight = mutation?.inFlight == true
    val canCreatePollBase = canManagePoll && poll?.isActive != true
    val canCreatePredictionBase = canManagePrediction &&
        prediction?.status !in setOf(PredictionStatus.ACTIVE, PredictionStatus.LOCKED)
    val canCreatePoll = canCreatePollBase && !mutationInFlight
    val canCreatePrediction = canCreatePredictionBase && !mutationInFlight
    if (
        poll == null &&
        prediction == null &&
        !canCreatePollBase &&
        !canCreatePredictionBase &&
        mutation == null
    ) return

    var creationKind by remember { mutableStateOf<InteractiveCreationKind?>(null) }
    var pendingManagementAction by remember(
        poll?.id,
        poll?.status,
        prediction?.id,
        prediction?.status,
    ) {
        mutableStateOf<PendingInteractiveManagementAction?>(null)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mutationInFlight) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LocalizedText(
                strings.working,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (mutation?.failed == true) {
            LocalizedText(
                strings.actionFailed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (canCreatePoll || canCreatePrediction) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canCreatePoll) {
                    OutlinedButton(onClick = { creationKind = InteractiveCreationKind.POLL }) {
                        LocalizedText(creationStrings.createPoll)
                    }
                }
                if (canCreatePrediction) {
                    OutlinedButton(onClick = { creationKind = InteractiveCreationKind.PREDICTION }) {
                        LocalizedText(creationStrings.createPrediction)
                    }
                }
            }
        }
        poll?.let {
            PollOverlayCard(
                poll = it,
                strings = strings,
                canManage = canManagePoll,
                enabled = !mutationInFlight,
                onEnd = {
                    pendingManagementAction = PendingInteractiveManagementAction(
                        InteractiveManagementAction.END_POLL,
                    )
                },
                onArchive = {
                    pendingManagementAction = PendingInteractiveManagementAction(
                        InteractiveManagementAction.ARCHIVE_POLL,
                    )
                },
            )
        }
        prediction?.let {
            PredictionOverlayCard(
                prediction = it,
                strings = strings,
                canManage = canManagePrediction,
                enabled = !mutationInFlight,
                onLock = {
                    pendingManagementAction = PendingInteractiveManagementAction(
                        InteractiveManagementAction.LOCK_PREDICTION,
                    )
                },
                onCancel = {
                    pendingManagementAction = PendingInteractiveManagementAction(
                        InteractiveManagementAction.CANCEL_PREDICTION,
                    )
                },
                onResolve = { outcomeId ->
                    pendingManagementAction = PendingInteractiveManagementAction(
                        action = InteractiveManagementAction.RESOLVE_PREDICTION,
                        outcomeId = outcomeId,
                    )
                },
            )
        }
    }

    creationKind?.let { kind ->
        InteractiveChatCreationDialog(
            kind = kind,
            strings = creationStrings,
            onDismiss = { creationKind = null },
            onCreatePoll = onCreatePoll,
            onCreatePrediction = onCreatePrediction,
        )
    }

    pendingManagementAction?.let { pending ->
        val actionLabel = when (pending.action) {
            InteractiveManagementAction.END_POLL -> strings.endPoll
            InteractiveManagementAction.ARCHIVE_POLL -> strings.archivePoll
            InteractiveManagementAction.LOCK_PREDICTION -> strings.lockPrediction
            InteractiveManagementAction.CANCEL_PREDICTION -> strings.cancelPrediction
            InteractiveManagementAction.RESOLVE_PREDICTION -> strings.resolvePrediction
        }
        AlertDialog(
            onDismissRequest = { pendingManagementAction = null },
            title = { LocalizedText(actionLabel) },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = {
                        pendingManagementAction = null
                        when (pending.action) {
                            InteractiveManagementAction.END_POLL -> onEndPoll()
                            InteractiveManagementAction.ARCHIVE_POLL -> onArchivePoll()
                            InteractiveManagementAction.LOCK_PREDICTION -> onLockPrediction()
                            InteractiveManagementAction.CANCEL_PREDICTION -> onCancelPrediction()
                            InteractiveManagementAction.RESOLVE_PREDICTION -> {
                                pending.outcomeId?.let(onResolvePrediction)
                            }
                        }
                    },
                ) {
                    LocalizedText(actionLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingManagementAction = null }) {
                    LocalizedText(creationStrings.cancel)
                }
            },
        )
    }
}

@Composable
private fun PollOverlayCard(
    poll: PollOverlay,
    strings: InteractiveOverlayUiStrings,
    canManage: Boolean,
    enabled: Boolean,
    onEnd: () -> Unit,
    onArchive: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocalizedText(
                strings.pollLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            VerbatimText(
                poll.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            poll.choices.forEach { choice ->
                val share = poll.voteShare(choice.id).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        VerbatimText(
                            choice.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        VerbatimText(
                            "${choice.votes} ${strings.votesLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { share },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (canManage && poll.isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = enabled, onClick = onEnd) {
                        LocalizedText(strings.endPoll)
                    }
                    TextButton(enabled = enabled, onClick = onArchive) {
                        LocalizedText(strings.archivePoll)
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionOverlayCard(
    prediction: PredictionOverlay,
    strings: InteractiveOverlayUiStrings,
    canManage: Boolean,
    enabled: Boolean,
    onLock: () -> Unit,
    onCancel: () -> Unit,
    onResolve: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocalizedText(
                strings.predictionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            VerbatimText(
                prediction.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            prediction.outcomes.forEach { outcome ->
                val share = prediction.pointsShare(outcome.id).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        VerbatimText(
                            outcome.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        VerbatimText(
                            "${outcome.users} ${strings.usersLabel} · ${outcome.channelPoints} ${strings.pointsLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { share },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canManage && prediction.status == PredictionStatus.LOCKED) {
                        TextButton(enabled = enabled, onClick = { onResolve(outcome.id) }) {
                            LocalizedText("${strings.resolvePrediction}: ${outcome.title}")
                        }
                    }
                }
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prediction.status == PredictionStatus.ACTIVE) {
                        OutlinedButton(enabled = enabled, onClick = onLock) {
                            LocalizedText(strings.lockPrediction)
                        }
                    }
                    if (prediction.status == PredictionStatus.ACTIVE || prediction.status == PredictionStatus.LOCKED) {
                        Spacer(Modifier.width(1.dp))
                        TextButton(enabled = enabled, onClick = onCancel) {
                            LocalizedText(strings.cancelPrediction)
                        }
                    }
                }
            }
        }
    }
}
