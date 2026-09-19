package io.ferventio.shared.runtime

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FerventioRuntimeHistoryTest {
    @Test
    fun clearLocalHistoryPreservesConnectionState() = runTest {
        val store = RecordingStore()
        val runtime = FerventioRuntimeState(history = store)
        runtime.chat.append(message("live"))
        runtime.chat.prependHistory("channel", listOf(message("history")))
        runtime.chat.updateConnection(
            status = ConnectionStatus.RECONNECTING,
            detail = "retrying",
            attempt = 2,
        )

        runtime.clearLocalHistory(listOf("channel"))

        assertEquals(1, store.clearAllCalls)
        assertEquals(emptyList(), runtime.chat.messages("channel"))
        assertEquals(ConnectionStatus.RECONNECTING, runtime.chat.connectionStatus)
        assertEquals("retrying", runtime.chat.connectionDetail)
        assertEquals(2, runtime.chat.connectionAttempt)
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
        timestamp = "2026-09-13T00:00:00Z",
        timestampMillis = if (id == "history") 1L else 2L,
    )

    private class RecordingStore : ChatHistoryStore {
        var clearAllCalls = 0

        override suspend fun saveMessage(message: ChatMessage, config: ChatHistoryConfig) = Unit

        override suspend fun saveMessages(messages: List<ChatMessage>, config: ChatHistoryConfig) = Unit

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
            clearAllCalls += 1
        }
    }
}
