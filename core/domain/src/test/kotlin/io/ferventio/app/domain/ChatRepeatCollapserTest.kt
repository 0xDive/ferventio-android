package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepeatCollapserTest {
    @Test
    fun `collapses three normalized consecutive messages into anchor`() {
        val messages = listOf(
            message("1", "alpha", "Kappa   Kappa", 1_000L),
            message("2", "beta", "kappa kappa", 2_000L),
            message("3", "gamma", "  KAPPA KAPPA  ", 3_000L),
        )

        val plan = ChatRepeatCollapser.build(messages)

        assertEquals(setOf("1"), plan.visibleMessageIds)
        assertEquals("1", plan.anchorFor("2"))
        assertEquals("1", plan.anchorFor("3"))
        assertEquals(3, plan.summaryFor("1")?.count)
        assertEquals(listOf("alpha", "beta", "gamma"), plan.summaryFor("1")?.participants?.map { it.displayName })
    }

    @Test
    fun `keeps run visible below threshold`() {
        val messages = listOf(
            message("1", "alpha", "same", 1_000L),
            message("2", "beta", "same", 2_000L),
        )

        val plan = ChatRepeatCollapser.build(messages)

        assertEquals(setOf("1", "2"), plan.visibleMessageIds)
        assertNull(plan.summaryFor("1"))
    }

    @Test
    fun `different message breaks a run`() {
        val messages = listOf(
            message("1", "alpha", "same", 1_000L),
            message("2", "beta", "same", 2_000L),
            message("3", "gamma", "different", 3_000L),
            message("4", "delta", "same", 4_000L),
        )

        val plan = ChatRepeatCollapser.build(messages)

        assertEquals(messages.map { it.id }.toSet(), plan.visibleMessageIds)
        assertTrue(plan.summariesByAnchorId.isEmpty())
    }

    @Test
    fun `does not collapse replies or protected roles`() {
        val messages = listOf(
            message("1", "alpha", "same", 1_000L),
            message("2", "beta", "same", 2_000L, reply = ReplyContext(parentMessageId = "p")),
            message("3", "mod", "same", 3_000L, badges = listOf(ChatBadge("moderator", "1"))),
            message("4", "gamma", "same", 4_000L),
            message("5", "delta", "same", 5_000L),
            message("6", "epsilon", "same", 6_000L),
        )

        val plan = ChatRepeatCollapser.build(messages)

        assertTrue("1" in plan.visibleMessageIds)
        assertTrue("2" in plan.visibleMessageIds)
        assertTrue("3" in plan.visibleMessageIds)
        assertTrue("4" in plan.visibleMessageIds)
        assertFalse("5" in plan.visibleMessageIds)
        assertFalse("6" in plan.visibleMessageIds)
        assertEquals(3, plan.summaryFor("4")?.count)
    }

    @Test
    fun `time window splits otherwise matching messages`() {
        val messages = listOf(
            message("1", "alpha", "same", 1_000L),
            message("2", "beta", "same", 2_000L),
            message("3", "gamma", "same", 12_001L),
        )

        val plan = ChatRepeatCollapser.build(messages, windowMillis = 10_000L)

        assertEquals(setOf("1", "2", "3"), plan.visibleMessageIds)
        assertTrue(plan.summariesByAnchorId.isEmpty())
    }

    @Test
    fun `participant list is bounded and deduplicated`() {
        val messages = listOf(
            message("1", "alpha", "same", 1_000L),
            message("2", "alpha", "same", 2_000L),
            message("3", "beta", "same", 3_000L),
            message("4", "gamma", "same", 4_000L),
        )

        val plan = ChatRepeatCollapser.build(messages, maxParticipants = 2)

        assertEquals(listOf("alpha", "beta"), plan.summaryFor("1")?.participants?.map { it.displayName })
    }

    private fun message(
        id: String,
        user: String,
        text: String,
        timestampMillis: Long,
        reply: ReplyContext? = null,
        badges: List<ChatBadge> = emptyList(),
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = user,
            login = user,
            displayName = user,
            badges = badges,
        ),
        text = text,
        timestamp = "2026-08-07T00:00:00Z",
        timestampMillis = timestampMillis,
        reply = reply,
    )
}
