package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionOverlay
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.interactive_poll_status_active
import io.ferventio.shared.generated.resources.interactive_poll_status_ended
import io.ferventio.shared.generated.resources.interactive_poll_title
import io.ferventio.shared.generated.resources.interactive_prediction_locked
import io.ferventio.shared.generated.resources.interactive_prediction_resolved
import io.ferventio.shared.generated.resources.interactive_prediction_status_active
import io.ferventio.shared.generated.resources.interactive_prediction_title
import io.ferventio.shared.generated.resources.interactive_prediction_users_points
import io.ferventio.shared.generated.resources.interactive_vote_count_percent
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

private const val MANAGE_POLLS_SCOPE = "channel:manage:polls"
private const val MANAGE_PREDICTIONS_SCOPE = "channel:manage:predictions"

@Composable
internal fun InteractiveChatOverlayCards(
    channelId: String,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val interactive = runtime.chat.interactiveState
    val poll = interactive.pollsByChannel[channelId]
    val prediction = interactive.predictionsByChannel[channelId]
    val session = runtime.authentication.state.authentication?.accessLease?.session
    val ownsChannel = session?.userId == channelId
    val canManageInteractive = ownsChannel &&
        (session?.scopes?.contains(MANAGE_POLLS_SCOPE) == true ||
            session?.scopes?.contains(MANAGE_PREDICTIONS_SCOPE) == true)
    if (poll == null && prediction == null && !canManageInteractive) return

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InteractiveMutationControls(
            channelId = channelId,
            poll = poll,
            prediction = prediction,
        )
        poll?.let { PollCard(it) }
        prediction?.let { PredictionCard(it) }
    }
}

@Composable
private fun PollCard(poll: PollOverlay) {
    OverlaySurface(
        eyebrow = stringResource(Res.string.interactive_poll_title),
        title = poll.title,
        status = when (poll.status) {
            PollStatus.ACTIVE -> stringResource(Res.string.interactive_poll_status_active)
            else -> stringResource(Res.string.interactive_poll_status_ended)
        },
    ) {
        poll.choices.forEach { choice ->
            val percent = (poll.voteShare(choice.id) * 100.0).toInt().coerceIn(0, 100)
            FactRow(
                label = choice.title,
                value = stringResource(
                    Res.string.interactive_vote_count_percent,
                    choice.votes,
                    percent,
                ),
                highlighted = false,
            )
        }
    }
}

@Composable
private fun PredictionCard(prediction: PredictionOverlay) {
    OverlaySurface(
        eyebrow = stringResource(Res.string.interactive_prediction_title),
        title = prediction.title,
        status = when (prediction.status) {
            PredictionStatus.ACTIVE -> stringResource(Res.string.interactive_prediction_status_active)
            PredictionStatus.LOCKED -> stringResource(Res.string.interactive_prediction_locked)
            PredictionStatus.RESOLVED -> stringResource(Res.string.interactive_prediction_resolved)
            PredictionStatus.CANCELED,
            PredictionStatus.UNKNOWN -> stringResource(Res.string.interactive_poll_status_ended)
        },
    ) {
        prediction.outcomes.forEach { outcome ->
            FactRow(
                label = outcome.title,
                value = stringResource(
                    Res.string.interactive_prediction_users_points,
                    outcome.users,
                    outcome.channelPoints,
                ),
                highlighted = prediction.winningOutcomeId == outcome.id,
            )
        }
    }
}

@Composable
private fun OverlaySurface(
    eyebrow: String,
    title: String,
    status: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = eyebrow,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
    highlighted: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
