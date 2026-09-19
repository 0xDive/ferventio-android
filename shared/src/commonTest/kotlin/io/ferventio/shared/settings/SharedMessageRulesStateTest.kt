package io.ferventio.shared.settings

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.MessageDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedMessageRulesStateTest {
    @Test
    fun restoringEditedRulesDoesNotReevaluateExistingDecorations() {
        val state = SharedMessageRulesStateHolder()
        val originalDecoration = MessageDecoration(
            highlightColorArgb = 0xFF112233L,
            highlightReasons = listOf("first"),
            playSound = true,
            addToMentions = true,
        )
        state.recordDecoration("message-id", originalDecoration)

        state.restore(
            SharedMessageRulesSnapshot(
                highlightRules = listOf(
                    HighlightRule(
                        id = "replacement",
                        type = HighlightRuleType.WORD,
                        pattern = "different",
                        colorArgb = 0xFF445566L,
                    ),
                ),
            ),
        )

        assertEquals(originalDecoration, state.decoration("message-id"))
    }

    @Test
    fun clearDropsRulesAndLiveDecorations() {
        val state = SharedMessageRulesStateHolder(
            SharedMessageRulesSnapshot(
                highlightRules = listOf(
                    HighlightRule(
                        id = "highlight",
                        type = HighlightRuleType.WORD,
                        pattern = "ping",
                    ),
                ),
            ),
        )
        state.recordDecoration(
            "message-id",
            MessageDecoration(highlightColorArgb = 0xFF112233L),
        )

        state.clear()

        assertTrue(state.highlightRules.isEmpty())
        assertTrue(state.ignoreRules.isEmpty())
        assertTrue(state.decorationsByMessageId.isEmpty())
    }
}
