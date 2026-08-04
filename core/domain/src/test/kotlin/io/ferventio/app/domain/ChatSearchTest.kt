package io.ferventio.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchTest {
    @Test
    fun `parser supports quoted operators and flags`() {
        val parsed = ChatSearchParser.parse(
            "from:SomeUser in:#Channel has:link is:deleted regex:\"hello world\" type:sub,raid",
            ZoneId.of("UTC"),
        ).getOrThrow()

        assertEquals("someuser", parsed.authorLogin)
        assertEquals("channel", parsed.channelLogin)
        assertTrue(parsed.hasLink)
        assertTrue(parsed.isDeleted)
        assertEquals("hello world", parsed.regexPattern)
        assertEquals(
            setOf(ChatMessageType.SUBSCRIPTION, ChatMessageType.RAID),
            parsed.types,
        )
    }

    @Test
    fun `plain words become case insensitive like filters`() {
        val request = ChatSearchRequest(rawQuery = "Hello 100%", limit = 25)
        val parsed = ChatSearchParser.parse(request.rawQuery).getOrThrow()
        val plan = ChatSearchSqlBuilder.build(request, parsed)

        assertTrue(plan.sql.contains("LOWER(text) LIKE ?"))
        assertTrue(plan.args.contains("%hello%"))
        assertTrue(plan.args.contains("%100\\%%"))
        assertEquals(25, plan.args.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `current channel search requires channel id`() {
        val request = ChatSearchRequest(
            rawQuery = "hello",
            scope = ChatSearchScope.CURRENT_CHANNEL,
        )
        val parsed = ChatSearchParser.parse(request.rawQuery).getOrThrow()
        ChatSearchSqlBuilder.build(request, parsed)
    }


    @Test
    fun `regex keeps backslash escapes`() {
        val parsed = ChatSearchParser.parse("regex:\"https?://\\S+\"").getOrThrow()
        assertEquals("https?://\\S+", parsed.regexPattern)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown operator is rejected`() {
        ChatSearchParser.parse("wat:value").getOrThrow()
    }

    @Test
    fun `date bounds are converted to inclusive days`() {
        val parsed = ChatSearchParser.parse(
            "after:2026-07-01 before:2026-07-03",
            ZoneId.of("UTC"),
        ).getOrThrow()

        assertEquals(1_782_864_000_000L, parsed.afterMillis)
        assertEquals(1_783_123_200_000L, parsed.beforeMillis)
    }
}
