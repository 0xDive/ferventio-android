package io.ferventio.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ferventio.app.domain.ChatRepeatSummary
import io.ferventio.shared.ui.chat.RepeatCountBadge

@Composable
internal fun ChatRepeatCountBadge(
    summary: ChatRepeatSummary,
    modifier: Modifier = Modifier,
) {
    RepeatCountBadge(
        count = summary.count,
        modifier = modifier,
    )
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
