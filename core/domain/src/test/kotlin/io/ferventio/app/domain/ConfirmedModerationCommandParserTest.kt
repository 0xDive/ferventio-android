package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedModerationCommandParserTest {
    @Test
    fun `ban keeps normalized login and reason`() {
        val result = ConfirmedModerationCommandParser.parse("/ban @Viewer repeated spam")
        assertTrue(result is ConfirmedModerationCommandParseResult.Success)
        val command = (result as ConfirmedModerationCommandParseResult.Success).command
        assertEquals(
            ConfirmedModerationCommand.Ban("viewer", "repeated spam"),
            command,
        )
    }

    @Test
    fun `timeout keeps parsed duration and reason`() {
        val result = ConfirmedModerationCommandParser.parse("/timeout viewer 5m cooldown")
        val command = (result as ConfirmedModerationCommandParseResult.Success).command
        assertEquals(
            ConfirmedModerationCommand.Timeout("viewer", 300, "cooldown"),
            command,
        )
    }

    @Test
    fun `room modes become typed commands`() {
        assertEquals(
            ConfirmedModerationCommand.Slow(20),
            (ConfirmedModerationCommandParser.parse("/slow 20") as ConfirmedModerationCommandParseResult.Success).command,
        )
        assertSame(
            ConfirmedModerationCommand.Subscribers,
            (ConfirmedModerationCommandParser.parse("/subscribers") as ConfirmedModerationCommandParseResult.Success).command,
        )
    }

    @Test
    fun `non moderation slash command is unsupported`() {
        assertSame(
            ConfirmedModerationCommandParseResult.Unsupported,
            ConfirmedModerationCommandParser.parse("/clip hello"),
        )
    }

    @Test
    fun `malformed moderation command returns parser error`() {
        assertTrue(
            ConfirmedModerationCommandParser.parse("/timeout") is ConfirmedModerationCommandParseResult.Error,
        )
    }
}
