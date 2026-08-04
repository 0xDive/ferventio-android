package io.ferventio.app.data.local

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatScrollPosition
import io.ferventio.app.domain.ChatSearchParser
import io.ferventio.app.domain.ChatSearchRequest
import io.ferventio.app.domain.ChatSearchSqlBuilder
import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.MessageRuleCodec
import io.ferventio.app.domain.LocalModerationAction
import androidx.sqlite.db.SimpleSQLiteQuery
import io.ferventio.app.domain.TwitchUser
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DatabaseStats(
    val pageSizeBytes: Long,
    val pageCount: Long,
    val freePageCount: Long,
    val messageCount: Int,
) {
    val usedBytes: Long
        get() = pageSizeBytes * (pageCount - freePageCount).coerceAtLeast(0L)

    val freeBytes: Long
        get() = pageSizeBytes * freePageCount.coerceAtLeast(0L)
}

class ChatHistoryRepository(
    private val database: FerventioDatabase,
) {
    private val dao = database.chatHistoryDao()
    private val writesSinceCleanup = AtomicInteger(0)
    private val lastVacuumAtMillis = AtomicLong(0L)
    private val maintenanceMutex = Mutex()

    suspend fun saveChannels(channels: List<ChatChannel>) {
        if (channels.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertChannelsPreservingHistory(channels.map { ChatHistoryMapper.toChannelEntity(it, now) })
    }

    suspend fun saveChannel(channel: ChatChannel) {
        dao.upsertChannelPreservingHistory(
            ChatHistoryMapper.toChannelEntity(channel, System.currentTimeMillis()),
        )
    }


    suspend fun loadAllChannels(): List<ChatChannel> =
        dao.loadAllChannels().map(::channelFromEntity)

    suspend fun loadChannels(logins: List<String>): List<ChatChannel> {
        val normalized = logins.map { it.trim().lowercase() }.filter(String::isNotBlank).distinct()
        if (normalized.isEmpty()) return emptyList()
        val byLogin = dao.loadChannelsByLogins(normalized).associateBy { it.login.lowercase() }
        return normalized.mapNotNull { login ->
            byLogin[login]?.let(::channelFromEntity)
        }
    }

    private fun channelFromEntity(entity: ChannelEntity): ChatChannel = ChatChannel(
        id = entity.id,
        login = entity.login,
        displayName = entity.displayName,
        profileImageUrl = entity.profileImageUrl,
    )

    suspend fun saveUsers(
        users: List<TwitchUser>,
        colorsByUserId: Map<String, String> = emptyMap(),
    ) {
        if (users.isEmpty()) return
        val now = System.currentTimeMillis()
        users.forEach { user ->
            val color = colorsByUserId[user.id]?.takeIf(String::isNotBlank)
            val entity = UserEntity(
                id = user.id,
                login = user.login,
                displayName = user.displayName,
                color = color,
                profileImageUrl = user.profileImageUrl,
                updatedAtMillis = now,
            )
            dao.insertUserIfMissing(entity)
            dao.updateUserProfile(
                userId = user.id,
                login = user.login,
                displayName = user.displayName,
                profileImageUrl = user.profileImageUrl,
                color = color,
                updatedAtMillis = now,
            )
        }
    }

    suspend fun saveMessage(
        message: ChatMessage,
        enabled: Boolean,
        limitPerChannel: Int,
        retentionDays: Int,
        maxDatabaseSizeMb: Int = 0,
    ) = saveMessages(
        messages = listOf(message),
        enabled = enabled,
        limitPerChannel = limitPerChannel,
        retentionDays = retentionDays,
        maxDatabaseSizeMb = maxDatabaseSizeMb,
    )

    suspend fun saveMessages(
        messages: List<ChatMessage>,
        enabled: Boolean,
        limitPerChannel: Int,
        retentionDays: Int,
        maxDatabaseSizeMb: Int = 0,
    ) {
        if (!enabled || messages.isEmpty()) return
        val validMessages = messages.filter { it.id.isNotBlank() && it.channelId.isNotBlank() }
        if (validMessages.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.replaceMessages(validMessages.map { ChatHistoryMapper.toWriteBundle(it, now) })
        if (writesSinceCleanup.addAndGet(validMessages.size) >= CLEANUP_EVERY_WRITES) {
            writesSinceCleanup.set(0)
            cleanup(
                channelIds = validMessages.map(ChatMessage::channelId).distinct(),
                limitPerChannel = limitPerChannel,
                retentionDays = retentionDays,
                maxDatabaseSizeMb = maxDatabaseSizeMb,
                nowMillis = now,
            )
        }
    }

    suspend fun loadRecentMessages(
        channelIds: List<String>,
        enabled: Boolean,
        limitPerChannel: Int,
        retentionDays: Int,
        maxDatabaseSizeMb: Int = 0,
    ): Map<String, List<ChatMessage>> {
        if (!enabled || channelIds.isEmpty()) return emptyMap()
        cleanup(channelIds, limitPerChannel, retentionDays, maxDatabaseSizeMb, System.currentTimeMillis())
        val initialPageSize = minOf(limitPerChannel, INITIAL_HISTORY_PAGE_SIZE)
        return channelIds.associateWith { channelId ->
            dao.loadRecentMessages(channelId, initialPageSize)
                .asReversed()
                .map(ChatHistoryMapper::fromDetails)
        }
    }

    suspend fun loadOlderMessages(
        channelId: String,
        beforeTimestampMillis: Long,
        beforeMessageId: String,
        limit: Int = HISTORY_PAGE_SIZE,
    ): List<ChatMessage> {
        if (channelId.isBlank() || beforeMessageId.isBlank()) return emptyList()
        return dao.loadMessagesBefore(
            channelId = channelId,
            timestampMillis = beforeTimestampMillis,
            messageId = beforeMessageId,
            limit = limit.coerceIn(1, HISTORY_PAGE_SIZE),
        ).asReversed().map(ChatHistoryMapper::fromDetails)
    }


    suspend fun searchMessages(request: ChatSearchRequest): Result<List<ChatMessage>> = runCatching {
        val parsed = ChatSearchParser.parse(request.rawQuery).getOrThrow()
        if (parsed.isEmpty && request.dateRange.days == null && request.messageTypes.isEmpty()) {
            return@runCatching emptyList()
        }
        val plan = ChatSearchSqlBuilder.build(request, parsed)
        val ids = dao.searchMessageIds(SimpleSQLiteQuery(plan.sql, plan.args.toTypedArray()))
        if (ids.isEmpty()) return@runCatching emptyList()
        val byId = ids.chunked(SEARCH_ID_BATCH_SIZE)
            .flatMap { batch -> dao.loadMessagesByIds(batch) }
            .associateBy { it.message.id }
        val regex = parsed.regexPattern?.let { Regex(it, RegexOption.IGNORE_CASE) }
        ids.asSequence()
            .mapNotNull(byId::get)
            .map(ChatHistoryMapper::fromDetails)
            .filter { message -> regex?.containsMatchIn(message.text) != false }
            .take(request.limit.coerceIn(1, 500))
            .toList()
    }

    suspend fun loadMessageContext(messageId: String, radius: Int = 3): List<ChatMessage> {
        val target = dao.loadMessageById(messageId) ?: return emptyList()
        val safeRadius = radius.coerceIn(1, 25)
        val before = dao.loadMessagesBefore(
            channelId = target.message.channelId,
            timestampMillis = target.message.timestampMillis,
            messageId = target.message.id,
            limit = safeRadius,
        ).asReversed()
        val after = dao.loadMessagesAfter(
            channelId = target.message.channelId,
            timestampMillis = target.message.timestampMillis,
            messageId = target.message.id,
            limit = safeRadius,
        )
        return (before + target + after).map(ChatHistoryMapper::fromDetails)
    }

    suspend fun saveAttentionEntries(entries: List<AttentionEntry>) {
        if (entries.isEmpty()) return
        dao.upsertAttentionEntries(entries.map { entry ->
            AttentionEntryEntity(
                messageId = entry.messageId,
                channelId = entry.channelId,
                channelLogin = entry.channelLogin,
                authorId = entry.authorId,
                authorLogin = entry.authorLogin,
                authorDisplayName = entry.authorDisplayName,
                text = entry.text,
                timestamp = entry.timestamp,
                timestampMillis = entry.timestampMillis,
                isRead = entry.isRead,
                isDirectMention = entry.isDirectMention,
                isHighlight = entry.isHighlight,
                highlightReasonsJson = MessageRuleCodec.encodeReasons(entry.highlightReasons),
                highlightColorArgb = entry.highlightColorArgb,
            )
        })
    }

    suspend fun loadAttentionEntries(limit: Int = 500): List<AttentionEntry> =
        dao.loadAttentionEntries(limit.coerceIn(1, 2_000)).map { entity ->
            AttentionEntry(
                messageId = entity.messageId,
                channelId = entity.channelId,
                channelLogin = entity.channelLogin,
                authorId = entity.authorId,
                authorLogin = entity.authorLogin,
                authorDisplayName = entity.authorDisplayName,
                text = entity.text,
                timestamp = entity.timestamp,
                timestampMillis = entity.timestampMillis,
                isRead = entity.isRead,
                isDirectMention = entity.isDirectMention,
                isHighlight = entity.isHighlight,
                highlightReasons = MessageRuleCodec.decodeReasons(entity.highlightReasonsJson),
                highlightColorArgb = entity.highlightColorArgb,
            )
        }

    suspend fun markAllAttentionRead() = dao.markAllAttentionRead()

    suspend fun markAttentionRead(messageId: String) {
        if (messageId.isNotBlank()) dao.markAttentionRead(messageId)
    }

    suspend fun markChannelAttentionRead(channelId: String) {
        if (channelId.isNotBlank()) dao.markChannelAttentionRead(channelId)
    }

    suspend fun saveScrollPosition(position: ChatScrollPosition) {
        if (position.channelId.isBlank()) return
        dao.upsertScrollState(
            ChannelScrollStateEntity(
                channelId = position.channelId,
                anchorMessageId = position.anchorMessageId?.takeIf(String::isNotBlank),
                firstVisibleItemIndex = position.firstVisibleItemIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = position.firstVisibleItemScrollOffset.coerceAtLeast(0),
                isAtBottom = position.isAtBottom,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun loadScrollPositions(channelIds: List<String>): Map<String, ChatScrollPosition> {
        if (channelIds.isEmpty()) return emptyMap()
        return dao.loadScrollStates(channelIds.distinct()).associate { entity ->
            entity.channelId to ChatScrollPosition(
                channelId = entity.channelId,
                anchorMessageId = entity.anchorMessageId,
                firstVisibleItemIndex = entity.firstVisibleItemIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = entity.firstVisibleItemScrollOffset.coerceAtLeast(0),
                isAtBottom = entity.isAtBottom,
            )
        }
    }

    suspend fun markMessageDeleted(channelId: String, messageId: String) {
        dao.markMessageDeleted(channelId, messageId, System.currentTimeMillis())
    }

    suspend fun markUserMessagesDeleted(channelId: String, userId: String) {
        dao.markUserMessagesDeleted(channelId, userId, System.currentTimeMillis())
    }

    suspend fun clearChannel(channelId: String) {
        dao.clearChannel(channelId)
    }

    suspend fun clearAll() {
        dao.clearAllHistory()
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("VACUUM")
        }
    }

    suspend fun countMessages(): Int = dao.countMessages()

    suspend fun compactIfNeeded(nowMillis: Long = System.currentTimeMillis()): Boolean =
        maintenanceMutex.withLock {
            val previous = lastVacuumAtMillis.get()
            if (previous > 0L && nowMillis - previous < VACUUM_MIN_INTERVAL_MILLIS) return@withLock false
            val pageSize = pragmaLong("page_size")
            val pageCount = pragmaLong("page_count")
            val freePages = pragmaLong("freelist_count")
            if (pageSize <= 0L || pageCount <= 0L) return@withLock false
            val freeBytes = pageSize * freePages
            val enoughWaste = freeBytes >= VACUUM_MIN_FREE_BYTES && freePages * 4L >= pageCount
            if (!enoughWaste) return@withLock false
            withContext(Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL("VACUUM")
            }
            lastVacuumAtMillis.set(nowMillis)
            true
        }

    suspend fun databaseStats(): DatabaseStats = DatabaseStats(
        pageSizeBytes = pragmaLong("page_size"),
        pageCount = pragmaLong("page_count"),
        freePageCount = pragmaLong("freelist_count"),
        messageCount = countMessages(),
    )

    suspend fun loadModerationActionsForUser(
        channelId: String,
        targetUserId: String,
        targetUserLogin: String,
        limit: Int = 50,
    ): List<LocalModerationAction> = dao.loadModerationActionsForUser(
        channelId = channelId,
        targetUserId = targetUserId,
        targetUserLogin = targetUserLogin,
        limit = limit.coerceIn(1, 200),
    ).map { entity ->
        LocalModerationAction(
            id = entity.id,
            channelId = entity.channelId,
            targetUserId = entity.targetUserId,
            targetUserLogin = entity.targetUserLogin,
            messageId = entity.messageId,
            action = entity.action,
            durationSeconds = entity.durationSeconds,
            reason = entity.reason,
            createdAtMillis = entity.createdAtMillis,
        )
    }

    suspend fun loadModerationActions(
        channelId: String,
        limit: Int = 200,
    ): List<LocalModerationAction> = dao.loadModerationActions(
        channelId = channelId,
        limit = limit.coerceIn(1, 500),
    ).map { entity ->
        LocalModerationAction(
            id = entity.id,
            channelId = entity.channelId,
            targetUserId = entity.targetUserId,
            targetUserLogin = entity.targetUserLogin,
            messageId = entity.messageId,
            action = entity.action,
            durationSeconds = entity.durationSeconds,
            reason = entity.reason,
            createdAtMillis = entity.createdAtMillis,
        )
    }

    suspend fun recordModerationAction(
        channelId: String,
        targetUserId: String? = null,
        targetUserLogin: String? = null,
        messageId: String? = null,
        action: String,
        durationSeconds: Int? = null,
        reason: String? = null,
    ) {
        dao.insertModerationAction(
            ModerationActionEntity(
                id = UUID.randomUUID().toString(),
                channelId = channelId,
                targetUserId = targetUserId,
                targetUserLogin = targetUserLogin,
                messageId = messageId,
                action = action,
                durationSeconds = durationSeconds,
                reason = reason,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun trimAll(
        channelIds: List<String>,
        limitPerChannel: Int,
        retentionDays: Int,
        maxDatabaseSizeMb: Int = 0,
    ) {
        cleanup(channelIds, limitPerChannel, retentionDays, maxDatabaseSizeMb, System.currentTimeMillis())
    }

    private suspend fun cleanup(
        channelIds: List<String>,
        limitPerChannel: Int,
        retentionDays: Int,
        maxDatabaseSizeMb: Int,
        nowMillis: Long,
    ) {
        val safeLimit = limitPerChannel.coerceIn(MIN_HISTORY_LIMIT, MAX_HISTORY_LIMIT)
        val safeDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        if (safeDays > 0) {
            val cutoff = nowMillis - safeDays * MILLIS_PER_DAY
            dao.deleteOlderThan(cutoff)
            dao.deleteModerationActionsOlderThan(cutoff)
            dao.deleteAttentionEntriesOlderThan(cutoff)
        }
        channelIds.distinct().forEach { channelId ->
            dao.trimChannel(channelId, safeLimit)
        }
        enforceMaxDatabaseSize(maxDatabaseSizeMb)
        dao.deleteOrphanUsers()
    }

    private suspend fun enforceMaxDatabaseSize(maxDatabaseSizeMb: Int) {
        val safeSizeMb = maxDatabaseSizeMb.coerceIn(0, MAX_DATABASE_SIZE_MB)
        if (safeSizeMb == 0) return
        val maxBytes = safeSizeMb.toLong() * 1_048_576L
        repeat(MAX_SIZE_TRIM_PASSES) {
            if (databaseUsedBytes() <= maxBytes) return
            val deleted = dao.deleteOldestMessages(SIZE_TRIM_BATCH) +
                dao.deleteOldestModerationActions(SIZE_TRIM_BATCH) +
                dao.deleteOldestAttentionEntries(SIZE_TRIM_BATCH)
            if (deleted <= 0) return
        }
    }

    private suspend fun databaseUsedBytes(): Long {
        val pageSize = pragmaLong("page_size")
        val pageCount = pragmaLong("page_count")
        val freePages = pragmaLong("freelist_count")
        return pageSize * (pageCount - freePages).coerceAtLeast(0L)
    }

    private suspend fun pragmaLong(name: String): Long = runCatching {
        dao.queryLong(SimpleSQLiteQuery("PRAGMA $name"))
    }.getOrDefault(0L)

    companion object {
        const val MIN_HISTORY_LIMIT = 100
        const val MAX_HISTORY_LIMIT = 5_000
        const val MIN_RETENTION_DAYS = 0
        const val MAX_RETENTION_DAYS = 365
        const val MAX_DATABASE_SIZE_MB = 1_024
        const val INITIAL_HISTORY_PAGE_SIZE = 200
        const val HISTORY_PAGE_SIZE = 200
        private const val CLEANUP_EVERY_WRITES = 20
        private const val SEARCH_ID_BATCH_SIZE = 400
        private const val SIZE_TRIM_BATCH = 250
        private const val MAX_SIZE_TRIM_PASSES = 80
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val VACUUM_MIN_INTERVAL_MILLIS = 7 * MILLIS_PER_DAY
        private const val VACUUM_MIN_FREE_BYTES = 16L * 1_048_576L
    }
}
