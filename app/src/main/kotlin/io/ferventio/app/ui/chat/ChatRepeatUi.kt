package io.ferventio.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ferventio.app.domain.ChatRepeatSummary
import io.ferventio.shared.ui.chat.RepeatCountBadge
import io.ferventio.shared.ui.chat.formatRepeatParticipantSummary

@Composable
internal fun ChatRepeatCountBadge(
    summary: ChatRepeatSummary,
    modifier: Modifier = Modifier,
) {
    RepeatCountBadge(summary = summary, modifier = modifier)
}

internal fun ChatRepeatParticipantSummary(summary: ChatRepeatSummary): String =
    formatRepeatParticipantSummary(summary)
