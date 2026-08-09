package io.ferventio.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.R
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.InteractiveChatCapabilities
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PredictionDraft
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.domain.interactiveChatCapabilities

/** Compact replacement for the removed Channel Points composer entry. */
@Composable
internal fun InteractiveChatCreationMenu(
    state: FerventioUiState,
    channelId: String,
    interactiveChatState: InteractiveChatOverlayState,
    onCreatePoll: (PollDraft) -> Unit,
    onCreatePrediction: (PredictionDraft) -> Unit,
) {
    val capabilities = state.session?.interactiveChatCapabilities(channelId) ?: InteractiveChatCapabilities()
    val poll = interactiveChatState.pollsByChannel[channelId]
    val prediction = interactiveChatState.predictionsByChannel[channelId]
    val mutationInFlight = interactiveChatState.mutationsByChannel[channelId]?.inFlight == true
    val canCreatePoll = capabilities.canManagePolls && poll?.isActive != true && !mutationInFlight
    val canCreatePrediction = capabilities.canManagePredictions &&
        prediction?.status !in setOf(PredictionStatus.ACTIVE, PredictionStatus.LOCKED) &&
        !mutationInFlight
    if (!canCreatePoll && !canCreatePrediction) return

    val resourceStrings = rememberAppResourceStrings(state.appLanguage)
    val strings = InteractiveCreationUiStrings(
        createPoll = resourceStrings.string(R.string.ferventio_interactive_create_poll),
        createPrediction = resourceStrings.string(R.string.ferventio_interactive_create_prediction),
        title = resourceStrings.string(R.string.ferventio_interactive_title),
        option = resourceStrings.string(R.string.ferventio_interactive_option),
        addOption = resourceStrings.string(R.string.ferventio_interactive_add_option),
        removeOption = resourceStrings.string(R.string.ferventio_interactive_remove_option),
        durationSeconds = resourceStrings.string(R.string.ferventio_interactive_duration_seconds),
        channelPointsVoting = resourceStrings.string(R.string.ferventio_interactive_channel_points_voting),
        pointsPerVote = resourceStrings.string(R.string.ferventio_interactive_points_per_vote),
        create = resourceStrings.string(R.string.ferventio_interactive_create),
        cancel = resourceStrings.string(R.string.ferventio_moderation_cancel),
    )
    var expanded by remember(channelId) { mutableStateOf(false) }
    var creationKind by remember(channelId) { mutableStateOf<InteractiveCreationKind?>(null) }

    Box {
        FilledTonalIconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Add,
                contentDescription = localizedString("Создать интерактив"),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (canCreatePoll) {
                DropdownMenuItem(
                    text = { LocalizedText(strings.createPoll) },
                    onClick = {
                        expanded = false
                        creationKind = InteractiveCreationKind.POLL
                    },
                )
            }
            if (canCreatePrediction) {
                DropdownMenuItem(
                    text = { LocalizedText(strings.createPrediction) },
                    onClick = {
                        expanded = false
                        creationKind = InteractiveCreationKind.PREDICTION
                    },
                )
            }
        }
    }
    Spacer(Modifier.width(6.dp))

    creationKind?.let { kind ->
        InteractiveChatCreationDialog(
            kind = kind,
            strings = strings,
            onDismiss = { creationKind = null },
            onCreatePoll = onCreatePoll,
            onCreatePrediction = onCreatePrediction,
        )
    }
}
