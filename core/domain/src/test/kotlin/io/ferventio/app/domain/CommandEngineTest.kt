package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEngineTest {
    @Test
    fun tokenizerKeepsQuotedArgumentsAndEscapes() {
        assertEquals(
            CommandTokenizationResult.Success(
                listOf("/settitle", "A stream title", "with spaces"),
            ),
            CommandTokenizer.tokenize("/settitle \"A stream title\" with\\ spaces"),
        )
    }

    @Test
    fun tokenizerReportsUnclosedQuote() {
        assertTrue(CommandTokenizer.tokenize("/settitle \"broken") is CommandTokenizationResult.Error)
    }

    @Test
    fun expanderSupportsPositionAndContextPlaceholders() {
        val result = CustomCommandExpander.expand(
            command = CustomCommand(
                name = "hello",
                template = "Привет, {1}! Канал {channel.name}; остаток: {2+}",
            ),
            arguments = listOf("Alice", "one", "two"),
            context = CustomCommandContext(
                channelName = "ferventio",
                channelId = "42",
                myName = "dive",
                myId = "7",
            ),
        )

        assertEquals(
            CustomCommandExpansionResult.Success("Привет, Alice! Канал ferventio; остаток: one two"),
            result,
        )
    }

    @Test
    fun codecRoundTripPreservesCommands() {
        val commands = listOf(
            CustomCommand("hello", "Hello {1+}", "Greeting", enabled = true),
            CustomCommand("off", "/me disabled", enabled = false),
        )

        assertEquals(commands.sortedBy(CustomCommand::normalizedName), CustomCommandCodec.decode(CustomCommandCodec.encode(commands)).getOrThrow())
    }

    @Test
    fun builtInNameCannotBeUsedByCustomCommand() {
        assertTrue(CustomCommandCodec.validate(CustomCommand("ban", "hello")).isFailure)
    }
}
