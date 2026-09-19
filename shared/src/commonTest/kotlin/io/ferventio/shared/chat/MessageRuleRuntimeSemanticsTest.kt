package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.IgnoreRuleType
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.TwitchSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageRuleRuntimeSemanticsTest {
    private val session = TwitchSession(
        clientId = "client-id",
        userId = "viewer-id",
        login = "viewer",
        scopes = emptySet(),
        expiresInSeconds = 3_600,
    )

    @Test
    fun ignoreWinsBeforeHighlightAndSuppressesMentions() {
        val evaluator = MessageRuleEvaluator.compile(
            highlights = listOf(
                HighlightRule(
                    id = "highlight",
                    type = HighlightRuleType.WORD,
                    pattern = "ping",
                    playSound = true,
                    push = true,
                    addToMentions = true,
                    filteredSplit = true,
                ),
            ),
            ignores = listOf(
                IgnoreRule(
                    id = "ignore",
                    type = IgnoreRuleType.WORD,
                    pattern = "ping",
                    displayMode = IgnoreDisplayMode.TAP_TO_REVEAL,
                ),
            ),
            session = session,
        )
        val message = message("ping @viewer")
        val decoration = evaluator.evaluate(message)

        assertTrue(decoration.isIgnored)
        assertEquals(IgnoreDisplayMode.TAP_TO_REVEAL, decoration.ignoreDisplayMode)
        assertFalse(decoration.isHighlighted)
        assertFalse(decoration.playSound)
        assertFalse(decoration.push)
        assertFalse(decoration.addToMentions)
        assertFalse(decoration.filteredSplit)

        val attention = ChatAttentionStateHolder()
        attention.recordIncoming(
            message = message,
            session = session,
            decoration = decoration,
            directMention = true,
        )

        assertEquals(1, attention.attention("channel-id").unreadCount)
        assertEquals(0, attention.attention("channel-id").mentionCount)
        assertTrue(attention.attentionEntries.isEmpty())
    }

    @Test
    fun matchingHighlightsKeepFirstColorAndAggregateActions() {
        val firstColor = 0xFF112233L
        val evaluator = MessageRuleEvaluator.compile(
            highlights = listOf(
                HighlightRule(
                    id = "first",
                    type = HighlightRuleType.WORD,
                    pattern = "ping",
                    colorArgb = firstColor,
                    playSound = true,
                ),
                HighlightRule(
                    id = "second",
                    type = HighlightRuleType.REGEX,
                    pattern = "p.ng",
                    colorArgb = 0xFF445566L,
                    push = true,
                    addToMentions = true,
                    filteredSplit = true,
                ),
            ),
            ignores = emptyList(),
            session = session,
        )

        val decoration = evaluator.evaluate(message("ping"))

        assertTrue(decoration.isHighlighted)
        assertEquals(firstColor, decoration.highlightColorArgb)
        assertEquals(2, decoration.highlightReasons.size)
        assertTrue(decoration.playSound)
        assertTrue(decoration.push)
        assertTrue(decoration.addToMentions)
        assertTrue(decoration.filteredSplit)
    }

    private fun message(text: String) = ChatMessage(
        id = "message-id",
        channelId = "channel-id",
        channelLogin = "channel",
        author = ChatAuthor("author-id", "author", "Author"),
        text = text,
        fragments = listOf(
            ChatFragment.Text(text.substringBefore("@viewer")),
            ChatFragment.Mention("@viewer", "viewer-id", "viewer", "viewer"),
        ),
        timestamp = "2026-08-20T15:30:00Z",
        timestampMillis = 1_000L,
    )
}
