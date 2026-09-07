package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ModerationAction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotChatHistoryStoreTest {
    private val config = ChatHistoryConfig(
        enabled = true,
        limitPerChannel = 500,
        retentionDays = 30,
    )

    @Test
    fun persistsSearchableMessagesAndContext() = runTest {
        val storage = MemorySnapshotStorage()
        val store = createStore(storage)
        val messages = listOf(
            message("1", "alpha", "alice", "hello world", 1_000L),
            message("2", "alpha", "bob", "visit https://example.invalid", 2_000L),
            message("3", "alpha", "alice", "subscription", 3_000L, ChatMessageType.SUBSCRIPTION),
            message("4", "beta", "alice", "other channel", 4_000L),
        )
        store.saveMessages(messages, config)

        val aliceAlpha = store.searchMessages(ChatHistorySearchRequest("from:alice in:alpha hello")).getOrThrow()
        val links = store.searchMessages(ChatHistorySearchRequest("has:link")).getOrThrow()
        val subscriptions = store.searchMessages(ChatHistorySearchRequest("is:sub")).getOrThrow()

        assertEquals(listOf("1"), aliceAlpha.map(ChatMessage::id))
        assertEquals(listOf("2"), links.map(ChatMessage::id))
        assertEquals(listOf("3"), subscriptions.map(ChatMessage::id))
        assertEquals(listOf("1", "2", "3"), store.loadMessageContext("2", radius = 1).map(ChatMessage::id))
        assertTrue(storage.value?.isNotBlank() == true)
    }

    @Test
    fun moderationMutationsPersistAcrossStoreInstances() = runTest {
        val storage = MemorySnapshotStorage()
        createStore(storage).apply {
            saveMessages(
                listOf(
                    message("1", "alpha", "alice", "one", 1_000L),
                    message("2", "alpha", "alice", "two", 2_000L),
                    message("3", "alpha", "bob", "three", 3_000L),
                ),
                config,
            )
            markMessageDeleted("alpha-id", "3")
            markUserMessagesDeleted("alpha-id", "alice-id")
        }

        val restored = createStore(storage).loadRecentMessages(listOf("alpha-id"), config).getValue("alpha-id")
        val byId = restored.associateBy(ChatMessage::id)

        assertEquals(ModerationAction.TIMEOUT, byId.getValue("1").moderation.action)
        assertEquals(ModerationAction.TIMEOUT, byId.getValue("2").moderation.action)
        assertEquals(ModerationAction.DELETE, byId.getValue("3").moderation.action)
        assertTrue(restored.all { it.flags.isDeleted })
        assertEquals(listOf("2", "1"), createStore(storage)
            .searchMessages(ChatHistorySearchRequest("is:timeout"))
            .getOrThrow()
            .map(ChatMessage::id))
    }

    @Test
    fun channelClearPhysicallyRemovesOnlyTargetChannel() = runTest {
        val storage = MemorySnapshotStorage()
        val store = createStore(storage)
        store.saveMessages(
            listOf(
                message("1", "alpha", "alice", "one", 1_000L),
                message("2", "beta", "bob", "two", 2_000L),
            ),
            config,
        )

        store.clearChannel("alpha-id")

        val restored = createStore(storage).loadRecentMessages(listOf("alpha-id", "beta-id"), config)
        assertTrue(restored.getValue("alpha-id").isEmpty())
        assertEquals(listOf("2"), restored.getValue("beta-id").map(ChatMessage::id))
    }

    @Test
    fun invalidOperatorAndMissingCurrentChannelFailSearch() = runTest {
        val store = createStore(MemorySnapshotStorage())
        store.saveMessage(message("1", "alpha", "alice", "hello", 1_000L), config)

        assertTrue(store.searchMessages(ChatHistorySearchRequest("wat:value")).isFailure)
        val missingChannel = store.searchMessages(
            ChatHistorySearchRequest(
                rawQuery = "hello",
                scope = io.ferventio.app.domain.ChatHistorySearchScope.CURRENT_CHANNEL,
                currentChannelId = null,
            ),
        )
        assertTrue(missingChannel.isFailure)
    }

    @Test
    fun disabledHistoryDoesNotWrite() = runTest {
        val storage = MemorySnapshotStorage()
        createStore(storage).saveMessage(
            message("1", "alpha", "alice", "hello", 1_000L),
            config.copy(enabled = false),
        )
        assertFalse(storage.value?.isNotBlank() == true)
    }

    private fun createStore(storage: MemorySnapshotStorage): SnapshotChatHistoryStore =
        SnapshotChatHistoryStore(
            storage = storage,
            localDateResolver = ChatHistoryLocalDateResolver { value ->
                when (value) {
                    "2026-08-19" -> 10_000L to 20_000L
                    "2026-08-20" -> 20_000L to 30_000L
                    else -> null
                }
            },
            nowMillis = { 100_000L },
        )

    private fun message(
        id: String,
        channelLogin: String,
        authorLogin: String,
        text: String,
        timestampMillis: Long,
        type: ChatMessageType = ChatMessageType.CHAT,
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "$channelLogin-id",
        channelLogin = channelLogin,
        author = ChatAuthor(
            id = "$authorLogin-id",
            login = authorLogin,
            displayName = authorLogin.replaceFirstChar { it.uppercase() },
        ),
        text = text,
        timestamp = "2026-08-19T00:00:00Z",
        timestampMillis = timestampMillis,
        type = type,
    )

    private class MemorySnapshotStorage : ChatHistorySnapshotStorage {
        var value: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }
}
