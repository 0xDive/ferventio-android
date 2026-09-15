package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotChatHistoryPagingTest {
    @Test
    fun olderPagesAreExclusiveOrderedAndExhaustible() = runTest {
        val storage = MemorySnapshotStorage()
        val store = SnapshotChatHistoryStore(
            storage = storage,
            localDateResolver = ChatHistoryLocalDateResolver { null },
            nowMillis = { 1_000_000L },
        )
        val config = ChatHistoryConfig(
            enabled = true,
            limitPerChannel = 500,
            retentionDays = 30,
        )
        store.saveMessages((1..450).map(::message), config)

        val recent = store.loadRecentMessages(listOf(CHANNEL_ID), config).getValue(CHANNEL_ID)
        assertEquals(200, recent.size)
        assertEquals("251", recent.first().id)
        assertEquals("450", recent.last().id)

        val firstOlder = store.loadOlderMessages(
            channelId = CHANNEL_ID,
            beforeTimestampMillis = recent.first().timestampMillis,
            beforeMessageId = recent.first().id,
            limit = 200,
        )
        assertEquals(200, firstOlder.size)
        assertEquals("51", firstOlder.first().id)
        assertEquals("250", firstOlder.last().id)

        val secondOlder = store.loadOlderMessages(
            channelId = CHANNEL_ID,
            beforeTimestampMillis = firstOlder.first().timestampMillis,
            beforeMessageId = firstOlder.first().id,
            limit = 200,
        )
        assertEquals(50, secondOlder.size)
        assertEquals("1", secondOlder.first().id)
        assertEquals("50", secondOlder.last().id)

        val exhausted = store.loadOlderMessages(
            channelId = CHANNEL_ID,
            beforeTimestampMillis = secondOlder.first().timestampMillis,
            beforeMessageId = secondOlder.first().id,
            limit = 200,
        )
        assertTrue(exhausted.isEmpty())
    }

    private fun message(index: Int): ChatMessage = ChatMessage(
        id = index.toString(),
        channelId = CHANNEL_ID,
        channelLogin = "alpha",
        author = ChatAuthor(
            id = "user-id",
            login = "user",
            displayName = "User",
        ),
        text = "message $index",
        timestamp = "2026-08-19T00:00:00Z",
        timestampMillis = index.toLong(),
    )

    private class MemorySnapshotStorage : ChatHistorySnapshotStorage {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    private companion object {
        const val CHANNEL_ID = "alpha-id"
    }
}
