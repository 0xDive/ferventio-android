package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatRepeatSummary

fun formatRepeatParticipantSummary(summary: ChatRepeatSummary): String {
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
