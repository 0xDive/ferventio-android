package io.ferventio.app.application

import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyHistoryWriteBatchTest {
    @Test
    fun batchCollectsMessagesAndAttentionInOneProjection() {
        val first = message("one")
        val second = message("two")
        val attention = AttentionEntry(
            messageId = "two",
            channelId = "channel",
            channelLogin = "channel",
            authorId = "user-two",
            authorLogin = "user_two",
            authorDisplayName = "User two",
            text = "two",
            timestamp = "2026-09-21T00:00:00Z",
            timestampMillis = 2L,
            isRead = false,
            isDirectMention = true,
        )

        val batch = collectLegacyHistoryWriteBatch(
            listOf(
                HistoryWriteRequest(first),
                HistoryWriteRequest(second, attention),
            ),
        )

        assertEquals(listOf("one", "two"), batch.messages.map(ChatMessage::id))
        assertEquals(listOf("two"), batch.attentionEntries.map(AttentionEntry::messageId))
    }

    @Test
    fun batchKeepsAttentionListSharedEmptyWhenNoAttentionExists() {
        val batch = collectLegacyHistoryWriteBatch(
            listOf(
                HistoryWriteRequest(message("one")),
                HistoryWriteRequest(message("two")),
            ),
        )

        assertTrue(batch.attentionEntries.isEmpty())
    }

    private fun message(id: String) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user_$id",
            displayName = "User $id",
        ),
        text = id,
        timestamp = "2026-09-21T00:00:00Z",
        timestampMillis = if (id == "one") 1L else 2L,
    )
}
