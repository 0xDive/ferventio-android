package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.InteractiveOverlayDraftValidator
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PredictionDraft

internal enum class InteractiveCreationKind {
    POLL,
    PREDICTION,
}

internal data class InteractiveCreationUiStrings(
    val createPoll: String,
    val createPrediction: String,
    val title: String,
    val option: String,
    val addOption: String,
    val removeOption: String,
    val durationSeconds: String,
    val channelPointsVoting: String,
    val pointsPerVote: String,
    val create: String,
    val cancel: String,
)

@Composable
internal fun InteractiveChatCreationDialog(
    kind: InteractiveCreationKind,
    strings: InteractiveCreationUiStrings,
    onDismiss: () -> Unit,
    onCreatePoll: (PollDraft) -> Unit,
    onCreatePrediction: (PredictionDraft) -> Unit,
) {
    var title by remember(kind) { mutableStateOf("") }
    var options by remember(kind) { mutableStateOf(listOf("", "")) }
    var durationText by remember(kind) {
        mutableStateOf(if (kind == InteractiveCreationKind.POLL) "60" else "120")
    }
    var channelPointsVoting by remember(kind) { mutableStateOf(false) }
    var pointsPerVoteText by remember(kind) { mutableStateOf("1") }

    val normalizedOptions = options.map(String::trim).filter(String::isNotBlank)
    val pollDraft = PollDraft(
        title = title.trim(),
        choices = normalizedOptions,
        durationSeconds = durationText.toIntOrNull() ?: 0,
        channelPointsVotingEnabled = channelPointsVoting,
        channelPointsPerVote = pointsPerVoteText.toIntOrNull() ?: 0,
    )
    val predictionDraft = PredictionDraft(
        title = title.trim(),
        outcomes = normalizedOptions,
        predictionWindowSeconds = durationText.toIntOrNull() ?: 0,
    )
    val isValid = when (kind) {
        InteractiveCreationKind.POLL -> InteractiveOverlayDraftValidator.validatePoll(pollDraft).isEmpty()
        InteractiveCreationKind.PREDICTION -> InteractiveOverlayDraftValidator.validatePrediction(predictionDraft).isEmpty()
    }
    val maxOptions = if (kind == InteractiveCreationKind.POLL) 5 else 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            LocalizedText(
                if (kind == InteractiveCreationKind.POLL) strings.createPoll else strings.createPrediction,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { LocalizedText(strings.title) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updated ->
                                options = options.toMutableList().also { it[index] = updated }
                            },
                            label = { LocalizedText("${strings.option} ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            TextButton(
                                onClick = {
                                    options = options.toMutableList().also { it.removeAt(index) }
                                },
                            ) {
                                LocalizedText(strings.removeOption)
                            }
                        }
                    }
                }
                if (options.size < maxOptions) {
                    TextButton(onClick = { options = options + "" }) {
                        LocalizedText(strings.addOption)
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                    label = { LocalizedText(strings.durationSeconds) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == InteractiveCreationKind.POLL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LocalizedText(
                            strings.channelPointsVoting,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = channelPointsVoting,
                            onCheckedChange = { channelPointsVoting = it },
                        )
                    }
                    if (channelPointsVoting) {
                        OutlinedTextField(
                            value = pointsPerVoteText,
                            onValueChange = { pointsPerVoteText = it.filter(Char::isDigit).take(7) },
                            label = { LocalizedText(strings.pointsPerVote) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    when (kind) {
                        InteractiveCreationKind.POLL -> onCreatePoll(pollDraft)
                        InteractiveCreationKind.PREDICTION -> onCreatePrediction(predictionDraft)
                    }
                    onDismiss()
                },
            ) {
                LocalizedText(strings.create)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                LocalizedText(strings.cancel)
            }
        },
    )
}
