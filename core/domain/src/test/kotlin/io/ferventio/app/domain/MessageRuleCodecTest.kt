package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageRuleCodecTest {
    @Test
    fun `highlight rules round trip`() {
        val rules = listOf(
            HighlightRule(
                id = "h1",
                type = HighlightRuleType.REGEX,
                pattern = "foo.*bar",
                enabled = false,
                caseSensitive = true,
                colorArgb = 0xFF123456L,
                playSound = true,
                push = true,
                addToMentions = false,
                filteredSplit = true,
            ),
        )
        assertEquals(rules, MessageRuleCodec.decodeHighlights(MessageRuleCodec.encodeHighlights(rules)))
    }

    @Test
    fun `ignore rules round trip`() {
        val rules = listOf(
            IgnoreRule(
                id = "i1",
                type = IgnoreRuleType.BOT_COMMAND,
                pattern = "!song",
                enabled = true,
                caseSensitive = false,
                displayMode = IgnoreDisplayMode.TAP_TO_REVEAL,
            ),
        )
        assertEquals(rules, MessageRuleCodec.decodeIgnores(MessageRuleCodec.encodeIgnores(rules)))
    }
}
