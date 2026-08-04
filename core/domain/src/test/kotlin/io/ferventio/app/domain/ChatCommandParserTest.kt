package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandParserTest {
    @Test
    fun regularMessageIsNotTreatedAsCommand() {
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.Message("hello chat")),
            ChatCommandParser.parse(" hello chat "),
        )
    }

    @Test
    fun parsesTimeoutWithMinutesAndReason() {
        assertEquals(
            ChatInputParseResult.Success(
                ParsedChatInput.Timeout("viewer", 300, "spam links"),
            ),
            ChatCommandParser.parse("/timeout @Viewer 5m spam links"),
        )
    }

    @Test
    fun timeoutWithoutDurationUsesTenMinutes() {
        assertEquals(
            ChatInputParseResult.Success(
                ParsedChatInput.Timeout("viewer", 600, "spam"),
            ),
            ChatCommandParser.parse("/timeout viewer spam"),
        )
    }

    @Test
    fun parsesBanReason() {
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.Ban("viewer", "hate speech")),
            ChatCommandParser.parse("/ban Viewer hate speech"),
        )
    }

    @Test
    fun parsesDurationUnits() {
        assertEquals(10, ChatCommandParser.parseDurationSeconds("10s"))
        assertEquals(300, ChatCommandParser.parseDurationSeconds("5m"))
        assertEquals(7_200, ChatCommandParser.parseDurationSeconds("2h"))
        assertEquals(86_400, ChatCommandParser.parseDurationSeconds("1d"))
    }

    @Test
    fun parsesQuotedTitle() {
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.SetTitle("A title with spaces")),
            ChatCommandParser.parse("/settitle \"A title with spaces\""),
        )
    }

    @Test
    fun parsesChatSettingsCommands() {
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.Slow(15)),
            ChatCommandParser.parse("/slow 15"),
        )
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.Followers(1_440)),
            ChatCommandParser.parse("/followers 1d"),
        )
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.EmoteOnly),
            ChatCommandParser.parse("/emoteonly"),
        )
    }

    @Test
    fun parsesEnabledCustomCommand() {
        assertEquals(
            ChatInputParseResult.Success(ParsedChatInput.Custom("hello", listOf("Alice", "Bob"))),
            ChatCommandParser.parse("/hello Alice Bob", setOf("hello")),
        )
    }

    @Test
    fun unknownSlashCommandReturnsReadableError() {
        val result = ChatCommandParser.parse("/something")
        assertTrue(result is ChatInputParseResult.Error)
    }
}
