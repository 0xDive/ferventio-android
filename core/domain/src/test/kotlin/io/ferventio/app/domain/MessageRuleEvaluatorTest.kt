package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageRuleEvaluatorTest {
    private val session = TwitchSession(
        clientId = "client",
        userId = "me-id",
        login = "my_login",
        scopes = emptySet(),
        expiresInSeconds = 3600,
    )

    @Test
    fun `direct mention and reply are detected`() {
        val evaluator = MessageRuleEvaluator.compile(emptyList(), emptyList(), session)
        assertTrue(evaluator.isDirectMention(message(text = "hello @my_login")))
        assertTrue(
            evaluator.isDirectMention(
                message(text = "hello", reply = ReplyContext("parent", parentUserId = "me-id")),
            ),
        )
        assertFalse(evaluator.isDirectMention(message(text = "hello @other")))
    }

    @Test
    fun `ignore wins before highlights`() {
        val evaluator = MessageRuleEvaluator.compile(
            highlights = listOf(
                HighlightRule(type = HighlightRuleType.WORD, pattern = "danger", playSound = true),
            ),
            ignores = listOf(
                IgnoreRule(
                    type = IgnoreRuleType.USER,
                    pattern = "bot",
                    displayMode = IgnoreDisplayMode.COLLAPSE,
                ),
            ),
            session = session,
        )
        val result = evaluator.evaluate(message(text = "danger", authorLogin = "bot"))
        assertEquals(IgnoreDisplayMode.COLLAPSE, result.ignoreDisplayMode)
        assertFalse(result.isHighlighted)
    }

    @Test
    fun `role reward bits and filtered split options are combined`() {
        val evaluator = MessageRuleEvaluator.compile(
            highlights = listOf(
                HighlightRule(
                    type = HighlightRuleType.MODERATOR,
                    filteredSplit = true,
                    addToMentions = false,
                ),
                HighlightRule(
                    type = HighlightRuleType.BITS,
                    push = true,
                    addToMentions = true,
                ),
            ),
            ignores = emptyList(),
            session = session,
        )
        val result = evaluator.evaluate(
            message(
                type = ChatMessageType.CHEER,
                badges = listOf(ChatBadge("moderator", "1")),
            ),
        )
        assertTrue(result.isHighlighted)
        assertTrue(result.filteredSplit)
        assertTrue(result.push)
        assertTrue(result.addToMentions)
        assertEquals(2, result.highlightReasons.size)
    }

    @Test
    fun `bot command and message type ignore rules work`() {
        val commandEvaluator = MessageRuleEvaluator.compile(
            highlights = emptyList(),
            ignores = listOf(
                IgnoreRule(type = IgnoreRuleType.BOT_COMMAND, pattern = "!song"),
            ),
            session = session,
        )
        assertTrue(commandEvaluator.evaluate(message(text = "!song now")).isIgnored)
        assertFalse(commandEvaluator.evaluate(message(text = "hello !song")).isIgnored)

        val typeEvaluator = MessageRuleEvaluator.compile(
            highlights = emptyList(),
            ignores = listOf(
                IgnoreRule(type = IgnoreRuleType.MESSAGE_TYPE, pattern = "SYSTEM"),
            ),
            session = session,
        )
        assertTrue(typeEvaluator.evaluate(message(type = ChatMessageType.SYSTEM)).isIgnored)
    }

    private fun message(
        text: String = "message",
        authorLogin: String = "author",
        type: ChatMessageType = ChatMessageType.CHAT,
        badges: List<ChatBadge> = emptyList(),
        reply: ReplyContext? = null,
    ): ChatMessage = ChatMessage(
        id = "id-${text.hashCode()}-${type.name}",
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "$authorLogin-id",
            login = authorLogin,
            displayName = authorLogin,
            badges = badges,
        ),
        text = text,
        timestamp = "2026-07-24T12:00:00Z",
        type = type,
        reply = reply,
    )
}
