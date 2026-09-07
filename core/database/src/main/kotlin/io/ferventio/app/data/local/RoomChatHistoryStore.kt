package io.ferventio.app.data.local

import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistoryDateRange
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistorySearchScope
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatSearchDateRange
import io.ferventio.app.domain.ChatSearchRequest
import io.ferventio.app.domain.ChatSearchScope

/**
 * Android adapter that preserves the existing Room v9 history database and all of its migrations.
 */
class RoomChatHistoryStore(
    private val repository: ChatHistoryRepository,
) : ChatHistoryStore {
    constructor(database: FerventioDatabase) : this(ChatHistoryRepository(database))

    override suspend fun saveMessage(message: ChatMessage, config: ChatHistoryConfig) {
        repository.saveMessage(
            message = message,
            enabled = config.enabled,
            limitPerChannel = config.limitPerChannel,
            retentionDays = config.retentionDays,
            maxDatabaseSizeMb = config.maxDatabaseSizeMb,
        )
    }

    override suspend fun saveMessages(messages: List<ChatMessage>, config: ChatHistoryConfig) {
        repository.saveMessages(
            messages = messages,
            enabled = config.enabled,
            limitPerChannel = config.limitPerChannel,
            retentionDays = config.retentionDays,
            maxDatabaseSizeMb = config.maxDatabaseSizeMb,
        )
    }

    override suspend fun loadRecentMessages(
        channelIds: List<String>,
        config: ChatHistoryConfig,
    ): Map<String, List<ChatMessage>> = repository.loadRecentMessages(
        channelIds = channelIds,
        enabled = config.enabled,
        limitPerChannel = config.limitPerChannel,
        retentionDays = config.retentionDays,
        maxDatabaseSizeMb = config.maxDatabaseSizeMb,
    )

    override suspend fun loadOlderMessages(
        channelId: String,
        beforeTimestampMillis: Long,
        beforeMessageId: String,
        limit: Int,
    ): List<ChatMessage> = repository.loadOlderMessages(
        channelId = channelId,
        beforeTimestampMillis = beforeTimestampMillis,
        beforeMessageId = beforeMessageId,
        limit = limit,
    )

    override suspend fun searchMessages(request: ChatHistorySearchRequest): Result<List<ChatMessage>> =
        repository.searchMessages(request.toLegacyRequest())

    override suspend fun loadMessageContext(messageId: String, radius: Int): List<ChatMessage> =
        repository.loadMessageContext(messageId, radius)

    override suspend fun markMessageDeleted(channelId: String, messageId: String) {
        repository.markMessageDeleted(channelId, messageId)
    }

    override suspend fun markUserMessagesDeleted(channelId: String, userId: String) {
        repository.markUserMessagesDeleted(channelId, userId)
    }

    override suspend fun clearChannel(channelId: String) {
        repository.clearChannel(channelId)
    }

    override suspend fun clearAll() {
        repository.clearAll()
    }
}

private fun ChatHistorySearchRequest.toLegacyRequest(): ChatSearchRequest = ChatSearchRequest(
    rawQuery = rawQuery,
    scope = when (scope) {
        ChatHistorySearchScope.CURRENT_CHANNEL -> ChatSearchScope.CURRENT_CHANNEL
        ChatHistorySearchScope.ALL_CHANNELS -> ChatSearchScope.ALL_CHANNELS
    },
    currentChannelId = currentChannelId,
    dateRange = when (dateRange) {
        ChatHistoryDateRange.ALL -> ChatSearchDateRange.ALL
        ChatHistoryDateRange.LAST_24_HOURS -> ChatSearchDateRange.LAST_24_HOURS
        ChatHistoryDateRange.LAST_7_DAYS -> ChatSearchDateRange.LAST_7_DAYS
        ChatHistoryDateRange.LAST_30_DAYS -> ChatSearchDateRange.LAST_30_DAYS
    },
    messageTypes = messageTypes,
    limit = limit,
    nowMillis = nowMillis,
)
