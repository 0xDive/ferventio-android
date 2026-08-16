package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatParticipantSummaryTest {
    @Test
    fun emptyNamesProduceEmptySummary() {
        assertEquals(
            "",
            formatRepeatParticipantSummary(
                displayNames = listOf("", "   "),
                omittedParticipantCount = 3,
            ),
        )
    }

    @Test
    fun visibleNamesAreJoinedInOrder() {
        assertEquals(
            "Alice, Bob",
            formatRepeatParticipantSummary(
                displayNames = listOf("Alice", "", "Bob"),
                omittedParticipantCount = 0,
            ),
        )
    }

    @Test
    fun omittedParticipantsAreAppended() {
        assertEquals(
            "Alice, Bob +4",
            formatRepeatParticipantSummary(
                displayNames = listOf("Alice", "Bob"),
                omittedParticipantCount = 4,
            ),
        )
    }
}
