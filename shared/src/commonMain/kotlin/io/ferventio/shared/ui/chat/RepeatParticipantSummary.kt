package io.ferventio.shared.ui.chat

fun formatRepeatParticipantSummary(
    displayNames: List<String>,
    omittedParticipantCount: Int,
): String {
    val visibleNames = displayNames
        .filter(String::isNotBlank)
        .joinToString(", ")

    return when {
        visibleNames.isBlank() -> ""
        omittedParticipantCount > 0 -> "$visibleNames +$omittedParticipantCount"
        else -> visibleNames
    }
}
