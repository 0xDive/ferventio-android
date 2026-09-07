package io.ferventio.app.domain

import kotlin.time.Clock

/**
 * Portable history configuration shared by Android Room and native persistence backends.
 *
 * The store owns clamping backend-specific limits. Keeping the raw user preference here avoids
 * leaking Room constants into shared runtime code.
 */
data class ChatHistoryConfig(
    val enabled: Boolean,
    val limitPerChannel: Int,
    val retentionDays: Int,
    val maxDatabaseSizeMb: Int = 0,
)

enum class ChatHistorySearchScope {
    CURRENT_CHANNEL,
    ALL_CHANNELS,
}

enum class ChatHistoryDateRange(val days: Int?) {
    ALL(null),
    LAST_24_HOURS(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

/**
 * Platform-neutral search request.
 *
 * [rawQuery] intentionally keeps the established operator syntax (from:, in:, has:, is:, regex:,
 * type:, after:, before:). Each backend must preserve those semantics rather than silently falling
 * back to plain-text search.
 */
data class ChatHistorySearchRequest(
    val rawQuery: String,
    val scope: ChatHistorySearchScope = ChatHistorySearchScope.ALL_CHANNELS,
    val currentChannelId: String? = null,
    val dateRange: ChatHistoryDateRange = ChatHistoryDateRange.ALL,
    val messageTypes: Set<ChatMessageType> = emptySet(),
    val limit: Int = 300,
    val nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
)

/**
 * Durable message-history boundary used by the multiplatform runtime.
 *
 * Android implements this by delegating to the existing Room v9 repository so no database schema
 * or file migration is required. Native platforms can use their own durable representation while
 * exposing the same logical [ChatMessage] snapshots.
 */
interface ChatHistoryStore {
    suspend fun saveMessage(message: ChatMessage, config: ChatHistoryConfig)

    suspend fun saveMessages(messages: List<ChatMessage>, config: ChatHistoryConfig)

    suspend fun loadRecentMessages(
        channelIds: List<String>,
        config: ChatHistoryConfig,
    ): Map<String, List<ChatMessage>>

    suspend fun loadOlderMessages(
        channelId: String,
        beforeTimestampMillis: Long,
        beforeMessageId: String,
        limit: Int = 200,
    ): List<ChatMessage>

    suspend fun searchMessages(request: ChatHistorySearchRequest): Result<List<ChatMessage>>

    suspend fun loadMessageContext(messageId: String, radius: Int = 3): List<ChatMessage>

    suspend fun markMessageDeleted(channelId: String, messageId: String)

    suspend fun markUserMessagesDeleted(channelId: String, userId: String)

    suspend fun clearChannel(channelId: String)

    suspend fun clearAll()
}
