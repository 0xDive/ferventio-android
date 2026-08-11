package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMentionParserTest {
    @Test
    fun `parses multiple independent mentions`() {
        val text = "@nick1 @nick2 @nick3 ку"

        assertEquals(
            listOf("nick1", "nick2", "nick3"),
            ChatMentionParser.findAll(text).map(ChatMention::login),
        )
    }

    @Test
    fun `resolves the mention under the tapped offset`() {
        val text = "ку @First, потом @second"

        assertEquals("First", ChatMentionParser.findAt(text, text.indexOf("First") + 2)?.login)
        assertEquals("second", ChatMentionParser.findAt(text, text.indexOf("second") + 2)?.login)
    }

    @Test
    fun `does not treat email-like text as a mention`() {
        val text = "mail@example.com"

        assertEquals(emptyList(), ChatMentionParser.findAll(text))
        assertNull(ChatMentionParser.findAt(text, text.indexOf('@')))
    }
}
