package io.ferventio.app.domain

import io.ferventio.app.testing.DeterministicFuzzer
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputParserFuzzTest {
    @Test
    fun randomizedCommandAndSearchInputsStayInsideParserContracts() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1301L)
        val zone = ZoneId.of("UTC")

        repeat(3_000) { iteration ->
            val raw = fuzz.text(maxLength = 500)

            val commandResult = ChatCommandParser.parse(
                rawInput = raw,
                customCommandNames = setOf("hello", "quote", "so"),
            )
            assertCommandResultIsBounded(commandResult, raw.length, iteration)

            val parsedResult = ChatSearchParser.parse(raw, zone)
            val parseFailure = parsedResult.exceptionOrNull()
            assertTrue(
                "ChatSearch escaped its Result contract at iteration $iteration: $parseFailure",
                parseFailure == null || parseFailure is Exception,
            )

            parsedResult.getOrNull()?.let { parsed ->
                val scope = if (fuzz.nextBoolean()) {
                    ChatSearchScope.ALL_CHANNELS
                } else {
                    ChatSearchScope.CURRENT_CHANNEL
                }
                val request = ChatSearchRequest(
                    rawQuery = raw,
                    scope = scope,
                    currentChannelId = if (scope == ChatSearchScope.CURRENT_CHANNEL) "channel-id" else null,
                    dateRange = ChatSearchDateRange.entries[fuzz.nextInt(0, ChatSearchDateRange.entries.size)],
                    limit = fuzz.nextInt(-1_000, 2_001),
                    nowMillis = 1_800_000_000_000L,
                )
                val plan = ChatSearchSqlBuilder.build(request, parsed)

                assertEquals(
                    "SQL placeholder mismatch at iteration $iteration",
                    plan.sql.count { it == '?' },
                    plan.args.size,
                )
                assertTrue(plan.candidateLimit in 1..5_000)
                assertTrue(plan.sql.length < 64_000)
            }
        }
    }

    @Test
    fun extremeDurationsCannotWrapIntoAcceptedRanges() {
        assertEquals(null, ChatCommandParser.parseDurationSeconds("${Long.MAX_VALUE}d"))
        assertEquals(null, ChatCommandParser.parseDurationSeconds("${Long.MAX_VALUE}h"))
        assertEquals(null, ChatCommandParser.parseFollowerMinutes("${Long.MAX_VALUE}mo"))
        assertEquals(null, ChatCommandParser.parseFollowerMinutes("${Long.MAX_VALUE}w"))
    }

    @Test
    fun searchLengthBoundaryIsDeterministic() {
        assertTrue(ChatSearchParser.parse("x".repeat(500)).isSuccess)
        val failure = ChatSearchParser.parse("x".repeat(501)).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun assertCommandResultIsBounded(
        result: ChatInputParseResult,
        sourceLength: Int,
        iteration: Int,
    ) {
        when (result) {
            is ChatInputParseResult.Error -> assertTrue(
                "Empty command error at iteration $iteration",
                result.message.isNotBlank(),
            )
            is ChatInputParseResult.Success -> when (val input = result.input) {
                is ParsedChatInput.Message -> assertTrue(input.text.length <= sourceLength)
                is ParsedChatInput.Action -> assertTrue(input.text.length <= sourceLength)
                is ParsedChatInput.Timeout -> assertTrue(input.durationSeconds in 1..1_209_600)
                is ParsedChatInput.Followers -> assertTrue(input.minutes in 0..129_600)
                else -> Unit
            }
        }
    }
}
