package io.ferventio.app.application

import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyAttentionRestoreTest {
    @Test
    fun matchingChannelIdsReuseOriginalList() {
        val entries = listOf(entry(channelId = "1", channelLogin = "alpha"))
        val channels = listOf(ChatChannel(id = "1", login = "alpha", displayName = "Alpha"))

        val result = remapLegacyAttentionEntries(entries, channels)

        assertTrue(result === entries)
    }

    @Test
    fun attentionSummaryAggregatesUnreadInOnePass() {
        val entries = listOf(
            entry(
                messageId = "later",
                channelId = "1",
                channelLogin = "alpha",
                timestampMillis = 20L,
            ),
            entry(
                messageId = "early",
                channelId = "1",
                channelLogin = "alpha",
                timestampMillis = 10L,
            ),
            entry(
                messageId = "capped",
                channelId = "1",
                channelLogin = "alpha",
                timestampMillis = 30L,
            ),
            entry(
                messageId = "read",
                channelId = "2",
                channelLogin = "beta",
                timestampMillis = 5L,
                isRead = true,
            ),
        )

        val summary = summarizeLegacyAttention(entries, maxCount = 2)

        assertEquals(3, summary.unreadCount)
        assertEquals(2, summary.channelAttention.getValue("1").unreadCount)
        assertEquals(2, summary.channelAttention.getValue("1").mentionCount)
        assertEquals("early", summary.channelAttention.getValue("1").firstUnreadMessageId)
        assertTrue("2" !in summary.channelAttention)
    }

    @Test
    fun legacyLoginRemapCopiesOnlyChangedEntry() {
        val unchanged = entry(
            messageId = "keep",
            channelId = "2",
            channelLogin = "beta",
        )
        val legacy = entry(
            messageId = "move",
            channelId = "legacy-id",
            channelLogin = "Alpha",
        )
        val entries = listOf(unchanged, legacy)
        val channels = listOf(
            ChatChannel(id = "1", login = "alpha", displayName = "Alpha"),
            ChatChannel(id = "2", login = "beta", displayName = "Beta"),
        )

        val result = remapLegacyAttentionEntries(entries, channels)

        assertTrue(result[0] === unchanged)
        assertEquals("1", result[1].channelId)
        assertEquals("move", result[1].messageId)
    }

    private fun entry(
        messageId: String = "message",
        channelId: String,
        channelLogin: String,
        timestampMillis: Long = 1L,
        isRead: Boolean = false,
    ) = AttentionEntry(
        messageId = messageId,
        channelId = channelId,
        channelLogin = channelLogin,
        authorId = "author",
        authorLogin = "author",
        authorDisplayName = "Author",
        text = "hello",
        timestamp = "2026-09-20T00:00:00Z",
        timestampMillis = timestampMillis,
        isRead = isRead,
        isDirectMention = true,
    )
}
