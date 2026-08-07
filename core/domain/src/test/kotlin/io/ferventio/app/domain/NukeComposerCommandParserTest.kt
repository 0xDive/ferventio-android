package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NukeComposerCommandParserTest {
    @Test
    fun `parses direct nuke query`() {
        val result = NukeComposerCommandParser.parse("/nuke repeated spam phrase")

        assertTrue(result is NukeComposerCommandParseResult.Success)
        val success = result as NukeComposerCommandParseResult.Success
        assertEquals("repeated spam phrase", success.config.query)
        assertEquals(NukeMatchMode.PLAIN_TEXT, success.config.matchMode)
    }

    @Test
    fun `nuke command is case insensitive`() {
        val result = NukeComposerCommandParser.parse("  /NuKe   KeepThis ")

        assertTrue(result is NukeComposerCommandParseResult.Success)
        assertEquals("KeepThis", (result as NukeComposerCommandParseResult.Success).config.query)
    }

    @Test
    fun `empty nuke query fails closed`() {
        val result = NukeComposerCommandParser.parse("/nuke   ")

        assertTrue(result is NukeComposerCommandParseResult.Error)
        assertEquals("Nuke query must not be empty", (result as NukeComposerCommandParseResult.Error).message)
    }

    @Test
    fun `other slash commands remain pass through candidates`() {
        assertTrue(NukeComposerCommandParser.parse("/nukebot hello") is NukeComposerCommandParseResult.NotNuke)
        assertTrue(NukeComposerCommandParser.parse("/somebot nuke hello") is NukeComposerCommandParseResult.NotNuke)
        assertTrue(NukeComposerCommandParser.parse("hello /nuke world") is NukeComposerCommandParseResult.NotNuke)
    }
}
