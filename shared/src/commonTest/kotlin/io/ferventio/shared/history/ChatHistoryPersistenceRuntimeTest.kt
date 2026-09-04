package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryPersistenceRuntimeTest {
    @Test
    fun flushDrainsAcceptedMutationsInOrder() = runTest {
        val store = RecordingStore()
        val runtime = ChatHistoryPersistenceRuntime(
            store = store,
            configProvider = { enabledConfig },
        )
        val first = message("1", 1_000L)
        val second = message("2", 2_000L)

        runtime.saveMessage(first)
        runtime.saveMessage(second)
        runtime.markMessageDeleted("channel", "1")
        runtime.markUserMessagesDeleted("channel", "user")
        runtime.clearChannel("channel")
        runtime.flushAndClose()

        assertEquals(
            listOf(
                "save:1",
                "save:2",
                "delete:channel:1",
                "timeout:channel:user",
                "clear:channel",
            ),
            store.operations,
        )
    }

    @Test
    fun restoreMergesPersistedHistoryWithoutReplacingLiveMessages() = runTest {
        val historical = message("history", 1_000L)
        val live = message("live", 2_000L)
        val store = RecordingStore(
            recent = mapOf("channel" to listOf(historical)),
        )
        val state = ChatRuntimeStateHolder().apply { append(live) }
        val runtime = ChatHistoryPersistenceRuntime(
            store = store,
            configProvider = { enabledConfig },
        )

        runtime.restoreRecent(state, listOf("channel"))
        runtime.flushAndClose()

        assertEquals(listOf("history", "live"), state.messages("channel").map(ChatMessage::id))
    }

    private fun message(id: String, timestampMillis: Long): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user",
            login = "user",
            displayName = "User",
        ),
        text = id,
        timestamp = "2026-08-19T00:00:00Z",
        timestampMillis = timestampMillis,
    )

    private class RecordingStore(
        private val recent: Map<String, List<ChatMessage>> = emptyMap(),
    ) : ChatHistoryStore {
        val operations = mutableListOf<String>()

        override suspend fun saveMessage(message: ChatMessage, config: ChatHistoryConfig) {
            operations += "save:${message.id}"
        }

        override suspend fun saveMessages(messages: List<ChatMessage>, config: ChatHistoryConfig) {
            messages.forEach { saveMessage(it, config) }
        }

        override suspend fun loadRecentMessages(
            channelIds: List<String>,
            config: ChatHistoryConfig,
        ): Map<String, List<ChatMessage>> = recent.filterKeys(channelIds.toSet()::contains)

        override suspend fun loadOlderMessages(
            channelId: String,
            beforeTimestampMillis: Long,
            beforeMessageId: String,
            limit: Int,
        ): List<ChatMessage> = emptyList()

        override suspend fun searchMessages(
            request: ChatHistorySearchRequest,
        ): Result<List<ChatMessage>> = Result.success(emptyList())

        override suspend fun loadMessageContext(messageId: String, radius: Int): List<ChatMessage> = emptyList()

        override suspend fun markMessageDeleted(channelId: String, messageId: String) {
            operations += "delete:$channelId:$messageId"
        }

        override suspend fun markUserMessagesDeleted(channelId: String, userId: String) {
            operations += "timeout:$channelId:$userId"
        }

        override suspend fun clearChannel(channelId: String) {
            operations += "clear:$channelId"
        }

        override suspend fun clearAll() {
            operations += "clear-all"
        }
    }

    private companion object {
        val enabledConfig = ChatHistoryConfig(
            enabled = true,
            limitPerChannel = 500,
            retentionDays = 7,
        )
    }
}
