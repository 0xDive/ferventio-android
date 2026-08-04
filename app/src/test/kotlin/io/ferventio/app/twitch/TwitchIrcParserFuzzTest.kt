package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.testing.DeterministicFuzzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchIrcParserFuzzTest {
    @Test
    fun arbitraryBoundedLinesNeverThrow() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1302L)

        repeat(2_500) { iteration ->
            val raw = fuzz.text(maxLength = 8_192)
            val events = TwitchIrcParser.parse(raw) { login -> "room:$login" }
            assertTrue("Too many events at iteration $iteration", events.size <= 2)
            assertMessagesReconstructText(events, iteration)
        }
    }

    @Test
    fun malformedEmoteRangesCannotLoseOrDuplicateMessageText() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1303L)

        repeat(1_500) { iteration ->
            val messageText = fuzz.text(maxLength = 160)
                .replace('\r', ' ')
                .replace('\n', ' ')
            val emoteTag = buildString {
                repeat(fuzz.nextInt(0, 12)) { group ->
                    if (group > 0) append('/')
                    append(fuzz.text(12)).append(':')
                    repeat(fuzz.nextInt(0, 8)) { range ->
                        if (range > 0) append(',')
                        append(fuzz.nextInt(-64, 256))
                        append('-')
                        append(fuzz.nextInt(-64, 256))
                    }
                }
            }
            val action = fuzz.nextBoolean()
            val payload = if (action) "\u0001ACTION $messageText\u0001" else messageText
            val line = "@display-name=Viewer;emotes=$emoteTag;id=fuzz-$iteration;login=viewer;" +
                "room-id=1234;tmi-sent-ts=1720000000000;user-id=55 " +
                ":viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :$payload"

            val events = TwitchIrcParser.parse(line) { null }
            assertMessagesReconstructText(events, iteration)
        }
    }

    @Test
    fun oversizedIrcLineIsRejectedBeforeParsing() {
        val result = TwitchIrcParser.parse("x".repeat(TwitchIrcParser.MAX_IRC_LINE_CHARS + 1)) { "room" }
        assertTrue(result.isEmpty())
    }

    private fun assertMessagesReconstructText(events: List<TwitchIrcEvent>, iteration: Int) {
        events.filterIsInstance<TwitchIrcEvent.Chat>().forEach { chat ->
            val event = chat.event
            if (event is ChatEvent.Message) {
                assertEquals(
                    "Fragment reconstruction mismatch at iteration $iteration",
                    event.message.text,
                    event.message.fragments.joinToString(separator = "") { fragment -> fragment.text },
                )
            }
        }
    }
}
