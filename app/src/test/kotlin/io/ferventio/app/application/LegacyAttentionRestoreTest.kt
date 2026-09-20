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
    ) = AttentionEntry(
        messageId = messageId,
        channelId = channelId,
        channelLogin = channelLogin,
        authorId = "author",
        authorLogin = "author",
        authorDisplayName = "Author",
        text = "hello",
        timestamp = "2026-09-20T00:00:00Z",
        timestampMillis = 1L,
        isRead = false,
        isDirectMention = true,
    )
}
