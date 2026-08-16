package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatRepeatParticipant
import io.ferventio.app.domain.ChatRepeatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatParticipantSummaryTest {
    @Test
    fun formatsVisibleParticipantsAndOmittedCount() {
        val summary = ChatRepeatSummary(
            anchorMessageId = "m1",
            count = 4,
            participants = listOf(
                ChatRepeatParticipant(userId = "u1", displayName = "Alice"),
                ChatRepeatParticipant(userId = "u2", displayName = "Bob"),
            ),
            totalParticipantCount = 3,
        )

        assertEquals("Alice, Bob +1", formatRepeatParticipantSummary(summary))
    }

    @Test
    fun skipsBlankParticipantNames() {
        val summary = ChatRepeatSummary(
            anchorMessageId = "m1",
            count = 3,
            participants = listOf(
                ChatRepeatParticipant(userId = "u1", displayName = ""),
                ChatRepeatParticipant(userId = "u2", displayName = "Bob"),
            ),
            totalParticipantCount = 2,
        )

        assertEquals("Bob", formatRepeatParticipantSummary(summary))
    }

    @Test
    fun returnsEmptyWhenNoVisibleNamesExist() {
        val summary = ChatRepeatSummary(
            anchorMessageId = "m1",
            count = 3,
            participants = emptyList(),
            totalParticipantCount = 0,
        )

        assertEquals("", formatRepeatParticipantSummary(summary))
    }
}
