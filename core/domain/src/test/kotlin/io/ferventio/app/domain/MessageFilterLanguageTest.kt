package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterLanguageTest {
    @Test
    fun `tokenizer recognizes operators strings regex and lists`() {
        val result = MessageFilterLanguage.tokenize(
            "author.badges contains [\"moderator\", \"vip\"] && message.content matches /hello/i",
        )

        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.tokens.any { it.kind == FilterTokenKind.KEYWORD_OPERATOR && it.lexeme == "contains" })
        assertTrue(result.tokens.any { it.kind == FilterTokenKind.REGEX })
        assertTrue(result.tokens.any { it.kind == FilterTokenKind.LEFT_BRACKET })
        assertTrue(result.tokens.any { it.lexeme == "&&" })
    }

    @Test
    fun `parser respects not and boolean precedence`() {
        val filter = MessageFilterLanguage.compile(
            "!flags.deleted && (author.badges contains \"moderator\" || author.badges contains \"vip\")",
        )

        assertTrue(filter.isValid)
        assertTrue(filter.matches(message(badges = listOf(ChatBadge("moderator", "1")))))
        assertFalse(filter.matches(message(badges = listOf(ChatBadge("vip", "1")), deleted = true)))
        assertFalse(filter.matches(message()))
    }

    @Test
    fun `all comparison and string operators evaluate`() {
        val message = message(text = "Hello Ferventio", displayName = "Alice")
        val expressions = listOf(
            "message.length > 5",
            "message.length >= 15",
            "message.length < 100",
            "message.length <= 15",
            "author.name == \"alice\"",
            "author.name != \"bob\"",
            "message.content contains \"ferventio\"",
            "message.content startswith \"hello\"",
            "message.content endswith \"Ferventio\"",
            "message.content matches /hello\\s+ferventio/i",
        )

        expressions.forEach { expression ->
            val compiled = MessageFilterLanguage.compile(expression)
            assertTrue("$expression: ${compiled.diagnostics}", compiled.isValid)
            assertTrue(expression, compiled.matches(message))
        }
    }

    @Test
    fun `string lists support badge containment and scalar membership`() {
        val message = message(displayName = "Alice", badges = listOf(ChatBadge("vip", "1")))

        assertTrue(
            MessageFilterLanguage.compile("author.badges contains [\"vip\"]").matches(message),
        )
        assertTrue(
            MessageFilterLanguage.compile("author.name == [\"bob\", \"alice\"]").matches(message),
        )
    }

    @Test
    fun `all requested message fields are available`() {
        val message = message(
            text = "reward",
            displayName = "Alice",
            channelLogin = "streamer",
            badges = listOf(ChatBadge("subscriber", "12")),
            type = ChatMessageType.REWARD,
            reply = ReplyContext(parentMessageId = "parent", parentUserName = "Bob"),
            reward = ChatReward(id = "reward-id", title = "Hydrate", cost = 2_000),
            moderation = ModerationState(action = ModerationAction.DELETE),
        )
        val expression = listOf(
            "message.content == \"reward\"",
            "message.length == 6",
            "author.name == \"alice\"",
            "author.id == \"user-id\"",
            "author.badges contains \"subscriber\"",
            "author.subbed == true",
            "channel.name == \"streamer\"",
            "channel.id == \"channel-id\"",
            "flags.deleted == false",
            "flags.reward_message == true",
            "flags.subscription == false",
            "flags.moderation == true",
            "reply.parent_author == \"bob\"",
            "reward.title == \"hydrate\"",
            "reward.cost >= 2000",
        ).joinToString(" && ")

        val compiled = MessageFilterLanguage.compile(expression)
        assertTrue(compiled.diagnostics.toString(), compiled.isValid)
        assertTrue(compiled.matches(message))
    }

    @Test
    fun `type checker reports unknown fields and invalid operands`() {
        val unknown = MessageFilterLanguage.compile("author.login == \"alice\"")
        val wrongType = MessageFilterLanguage.compile("message.length contains \"1\"")
        val nonBoolean = MessageFilterLanguage.compile("message.content")

        assertFalse(unknown.isValid)
        assertTrue(unknown.diagnostics.any { "Неизвестное поле" in it.message })
        assertFalse(wrongType.isValid)
        assertTrue(wrongType.diagnostics.any { "contains" in it.message })
        assertFalse(nonBoolean.isValid)
        assertTrue(nonBoolean.diagnostics.any { "Boolean" in it.message })
    }

    @Test
    fun `diagnostics include source positions`() {
        val compiled = MessageFilterLanguage.compile("message.content == ")

        assertFalse(compiled.isValid)
        assertTrue(compiled.diagnostics.any { it.span.start >= 0 && it.span.endExclusive >= it.span.start })
    }

    @Test
    fun `legacy split search remains compatible`() {
        val compiled = MessageFilterLanguage.compileForSplit("alice")

        assertTrue(compiled.isValid)
        assertTrue(compiled.isLegacyTextFilter)
        assertTrue(compiled.matches(message(displayName = "Alice")))
        assertFalse(compiled.matches(message(displayName = "Bob")))
    }

    @Test
    fun `saved filter references resolve to current expression`() {
        val filter = SavedMessageFilter(id = "filter-id", name = "Mods", expression = "author.subbed == true")
        val reference = savedFilterReference(filter.id)

        assertEquals("filter-id", savedFilterIdFromReference(reference))
        assertEquals(filter.expression, resolveSplitFilterExpression(reference, listOf(filter)))
        assertEquals("message.length > 10", resolveSplitFilterExpression("message.length > 10", listOf(filter)))
    }

    @Test
    fun `missing reward metadata never crashes and does not match comparisons`() {
        val compiled = MessageFilterLanguage.compile("reward.cost > 100")

        assertTrue(compiled.isValid)
        assertFalse(compiled.matches(message(type = ChatMessageType.REWARD)))
    }

    private fun message(
        text: String = "hello",
        displayName: String = "User",
        channelLogin: String = "channel",
        badges: List<ChatBadge> = emptyList(),
        deleted: Boolean = false,
        type: ChatMessageType = ChatMessageType.CHAT,
        reply: ReplyContext? = null,
        reward: ChatReward? = null,
        moderation: ModerationState = ModerationState(),
    ) = ChatMessage(
        id = "message-id",
        channelId = "channel-id",
        channelLogin = channelLogin,
        author = ChatAuthor(
            id = "user-id",
            login = displayName.lowercase(),
            displayName = displayName,
            badges = badges,
        ),
        text = text,
        timestamp = "2026-07-24T12:00:00Z",
        timestampMillis = 1L,
        reply = reply,
        reward = reward,
        type = type,
        flags = MessageFlags(isDeleted = deleted),
        moderation = moderation,
    )
}
