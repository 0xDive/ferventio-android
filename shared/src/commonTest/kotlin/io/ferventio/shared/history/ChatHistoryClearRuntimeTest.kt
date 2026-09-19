package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryClearRuntimeTest {
    @Test
    fun clearAllRunsAfterPreviouslyAcceptedWrites() = runTest {
        val store = RecordingStore()
        val boundStore = RuntimeBoundChatHistoryStore(store)
        val runtime = ChatHistoryPersistenceRuntime(
            store = boundStore,
            configProvider = { enabledConfig },
        )

        runtime.saveMessage(message("before-clear"))
        val clear = async { boundStore.clearAll() }
        clear.await()
        runtime.saveMessage(message("after-clear"))
        runtime.flushAndClose()

        assertEquals(
            listOf("save:before-clear", "clear-all", "save:after-clear"),
            store.operations,
        )
    }

    private fun message(id: String): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user",
            login = "user",
            displayName = "User",
        ),
        text = id,
        timestamp = "2026-09-07T00:00:00Z",
        timestampMillis = 1L,
    )

    private class RecordingStore : ChatHistoryStore {
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
        ): Map<String, List<ChatMessage>> = emptyMap()

        override suspend fun loadOlderMessages(
            channelId: String,
            beforeTimestampMillis: Long,
            beforeMessageId: String,
            limit: Int,
        ): List<ChatMessage> = emptyList()

        override suspend fun searchMessages(request: ChatHistorySearchRequest): Result<List<ChatMessage>> =
            Result.success(emptyList())

        override suspend fun loadMessageContext(messageId: String, radius: Int): List<ChatMessage> = emptyList()

        override suspend fun markMessageDeleted(channelId: String, messageId: String) = Unit

        override suspend fun markUserMessagesDeleted(channelId: String, userId: String) = Unit

        override suspend fun clearChannel(channelId: String) = Unit

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
