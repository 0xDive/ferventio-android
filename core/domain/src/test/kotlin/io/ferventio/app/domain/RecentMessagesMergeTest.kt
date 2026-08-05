package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentMessagesMergeTest {
    @Test
    fun `merges chronologically and existing message wins duplicate`() {
        val recentDuplicate = message("same", 1_000L, "remote")
        val existingDuplicate = message("same", 1_000L, "live")
        val recentOlder = message("older", 500L, "older")
        val existingNewer = message("newer", 1_500L, "newer")

        val result = RecentMessagesMerge.merge(
            existing = listOf(existingDuplicate, existingNewer),
            recent = listOf(recentDuplicate, recentOlder),
            limit = 10,
        )

        assertEquals(listOf("older", "same", "newer"), result.messages.map(ChatMessage::id))
        assertEquals("live", result.messages.first { it.id == "same" }.text)
        assertEquals(listOf("older"), result.addedMessages.map(ChatMessage::id))
    }

    @Test
    fun `applies memory limit after combining both sources`() {
        val result = RecentMessagesMerge.merge(
            existing = listOf(message("live", 4_000L, "live")),
            recent = listOf(
                message("one", 1_000L, "one"),
                message("two", 2_000L, "two"),
                message("three", 3_000L, "three"),
            ),
            limit = 2,
        )

        assertEquals(listOf("three", "live"), result.messages.map(ChatMessage::id))
        assertEquals(listOf("three"), result.addedMessages.map(ChatMessage::id))
    }

    private fun message(id: String, timestampMillis: Long, text: String) = ChatMessage(
        id = id,
        channelId = "channel-id",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-id",
            login = "viewer",
            displayName = "Viewer",
        ),
        text = text,
        timestamp = java.time.Instant.ofEpochMilli(timestampMillis).toString(),
        timestampMillis = timestampMillis,
    )
}
