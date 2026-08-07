package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatRepeatSummary

@Composable
internal fun ChatRepeatCountBadge(
    summary: ChatRepeatSummary,
    modifier: Modifier = Modifier,
) {
    if (summary.count < 2) return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerbatimText(
                text = "×${summary.count}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun ChatRepeatParticipantSummary(summary: ChatRepeatSummary): String {
    if (summary.participants.isEmpty()) return ""

    val visibleNames = summary.participants
        .map { participant -> participant.displayName }
        .filter(String::isNotBlank)
        .joinToString(", ")

    return when {
        visibleNames.isBlank() -> ""
        summary.omittedParticipantCount > 0 -> "$visibleNames +${summary.omittedParticipantCount}"
        else -> visibleNames
    }
}
