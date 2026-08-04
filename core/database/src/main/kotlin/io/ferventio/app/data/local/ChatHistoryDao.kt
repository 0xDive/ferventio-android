package io.ferventio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class ChatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChannelsIfMissing(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChannelIfMissing(channel: ChannelEntity)

    @Query(
        """
        UPDATE channels
        SET login = :login,
            display_name = :displayName,
            profile_image_url = :profileImageUrl,
            updated_at_millis = :updatedAtMillis
        WHERE id = :channelId
        """,
    )
    abstract suspend fun updateChannelMetadata(
        channelId: String,
        login: String,
        displayName: String,
        profileImageUrl: String?,
        updatedAtMillis: Long,
    )

    @Transaction
    open suspend fun upsertChannelsPreservingHistory(channels: List<ChannelEntity>) {
        if (channels.isEmpty()) return
        insertChannelsIfMissing(channels)
        channels.forEach { channel ->
            updateChannelMetadata(
                channelId = channel.id,
                login = channel.login,
                displayName = channel.displayName,
                profileImageUrl = channel.profileImageUrl,
                updatedAtMillis = channel.updatedAtMillis,
            )
        }
    }

    @Transaction
    open suspend fun upsertChannelPreservingHistory(channel: ChannelEntity) {
        insertChannelIfMissing(channel)
        updateChannelMetadata(
            channelId = channel.id,
            login = channel.login,
            displayName = channel.displayName,
            profileImageUrl = channel.profileImageUrl,
            updatedAtMillis = channel.updatedAtMillis,
        )
    }

    @Query("SELECT * FROM channels WHERE login IN (:logins)")
    abstract suspend fun loadChannelsByLogins(logins: List<String>): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY rowid ASC")
    abstract suspend fun loadAllChannels(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUserIfMissing(user: UserEntity)

    @Query(
        """
        UPDATE users
        SET login = :login,
            display_name = :displayName,
            profile_image_url = :profileImageUrl,
            color = CASE WHEN :color IS NULL OR :color = '' THEN color ELSE :color END,
            updated_at_millis = :updatedAtMillis
        WHERE id = :userId
        """,
    )
    abstract suspend fun updateUserProfile(
        userId: String,
        login: String,
        displayName: String,
        profileImageUrl: String?,
        color: String?,
        updatedAtMillis: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBadges(badges: List<ChatBadgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFragments(fragments: List<ChatFragmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertModerationAction(action: ModerationActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAttentionEntry(entry: AttentionEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAttentionEntries(entries: List<AttentionEntryEntity>)

    @Query(
        """
        SELECT * FROM attention_entries
        ORDER BY timestamp_millis DESC, message_id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadAttentionEntries(limit: Int): List<AttentionEntryEntity>

    @Query("UPDATE attention_entries SET is_read = 1 WHERE is_read = 0")
    abstract suspend fun markAllAttentionRead()

    @Query("UPDATE attention_entries SET is_read = 1 WHERE message_id = :messageId")
    abstract suspend fun markAttentionRead(messageId: String)

    @Query("UPDATE attention_entries SET is_read = 1 WHERE channel_id = :channelId AND is_read = 0")
    abstract suspend fun markChannelAttentionRead(channelId: String)

    @Query("SELECT COUNT(*) FROM attention_entries WHERE is_read = 0")
    abstract suspend fun countUnreadAttention(): Int

    @Query(
        """
        SELECT * FROM moderation_actions
        WHERE channel_id = :channelId
          AND (
              (:targetUserId != '' AND target_user_id = :targetUserId)
              OR (:targetUserLogin != '' AND LOWER(target_user_login) = LOWER(:targetUserLogin))
          )
        ORDER BY created_at_millis DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadModerationActionsForUser(
        channelId: String,
        targetUserId: String,
        targetUserLogin: String,
        limit: Int,
    ): List<ModerationActionEntity>

    @Query(
        """
        SELECT * FROM moderation_actions
        WHERE channel_id = :channelId
        ORDER BY created_at_millis DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadModerationActions(
        channelId: String,
        limit: Int,
    ): List<ModerationActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertScrollState(state: ChannelScrollStateEntity)

    @Query("SELECT * FROM channel_scroll_state WHERE channel_id IN (:channelIds)")
    abstract suspend fun loadScrollStates(channelIds: List<String>): List<ChannelScrollStateEntity>

    @Query("DELETE FROM chat_badges WHERE message_id = :messageId")
    abstract suspend fun deleteBadges(messageId: String)

    @Query("DELETE FROM chat_badges WHERE message_id IN (:messageIds)")
    abstract suspend fun deleteBadgesForMessages(messageIds: List<String>)

    @Query("DELETE FROM chat_fragments WHERE message_id = :messageId")
    abstract suspend fun deleteFragments(messageId: String)

    @Query("DELETE FROM chat_fragments WHERE message_id IN (:messageIds)")
    abstract suspend fun deleteFragmentsForMessages(messageIds: List<String>)

    @Transaction
    open suspend fun replaceMessage(bundle: MessageWriteBundle) = replaceMessages(listOf(bundle))

    @Transaction
    open suspend fun replaceMessages(bundles: List<MessageWriteBundle>) {
        if (bundles.isEmpty()) return
        val channels = bundles.asSequence().map(MessageWriteBundle::channel).distinctBy(ChannelEntity::id).toList()
        val users = bundles.asSequence().map(MessageWriteBundle::user).distinctBy(UserEntity::id).toList()
        val messages = bundles.map(MessageWriteBundle::message)
        val messageIds = messages.map(ChatMessageEntity::id)

        insertChannelsIfMissing(channels)
        channels.forEach { channel ->
            updateChannelMetadata(
                channelId = channel.id,
                login = channel.login,
                displayName = channel.displayName,
                profileImageUrl = channel.profileImageUrl,
                updatedAtMillis = channel.updatedAtMillis,
            )
        }
        upsertUsers(users)
        upsertMessages(messages)
        deleteBadgesForMessages(messageIds)
        deleteFragmentsForMessages(messageIds)

        val badges = bundles.flatMap(MessageWriteBundle::badges)
        val fragments = bundles.flatMap(MessageWriteBundle::fragments)
        if (badges.isNotEmpty()) insertBadges(badges)
        if (fragments.isNotEmpty()) insertFragments(fragments)
    }

    @Transaction
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE channel_id = :channelId
        ORDER BY timestamp_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadRecentMessages(
        channelId: String,
        limit: Int,
    ): List<MessageWithDetails>


    @RawQuery
    abstract suspend fun searchMessageIds(query: SupportSQLiteQuery): List<String>

    @RawQuery
    abstract suspend fun queryLong(query: SupportSQLiteQuery): Long

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE id IN (:messageIds)")
    abstract suspend fun loadMessagesByIds(messageIds: List<String>): List<MessageWithDetails>

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    abstract suspend fun loadMessageById(messageId: String): MessageWithDetails?

    @Transaction
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE channel_id = :channelId
          AND (
              timestamp_millis < :timestampMillis
              OR (timestamp_millis = :timestampMillis AND id < :messageId)
          )
        ORDER BY timestamp_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadMessagesBefore(
        channelId: String,
        timestampMillis: Long,
        messageId: String,
        limit: Int,
    ): List<MessageWithDetails>

    @Transaction
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE channel_id = :channelId
          AND (
              timestamp_millis > :timestampMillis
              OR (timestamp_millis = :timestampMillis AND id > :messageId)
          )
        ORDER BY timestamp_millis ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun loadMessagesAfter(
        channelId: String,
        timestampMillis: Long,
        messageId: String,
        limit: Int,
    ): List<MessageWithDetails>

    @Query(
        """
        UPDATE chat_messages
        SET is_deleted = 1,
            moderation_action = 'DELETE',
            moderation_at_millis = :atMillis
        WHERE id = :messageId AND channel_id = :channelId
        """,
    )
    abstract suspend fun markMessageDeleted(channelId: String, messageId: String, atMillis: Long)

    @Query(
        """
        UPDATE chat_messages
        SET is_deleted = 1,
            moderation_action = 'TIMEOUT',
            moderation_at_millis = :atMillis
        WHERE channel_id = :channelId AND author_id = :userId
        """,
    )
    abstract suspend fun markUserMessagesDeleted(channelId: String, userId: String, atMillis: Long)

    @Query("DELETE FROM chat_messages WHERE channel_id = :channelId")
    abstract suspend fun clearChannel(channelId: String)

    @Query("DELETE FROM chat_messages")
    abstract suspend fun clearAllMessages()

    @Query("DELETE FROM moderation_actions")
    abstract suspend fun clearAllModerationActions()

    @Query("DELETE FROM attention_entries")
    abstract suspend fun clearAllAttentionEntries()

    @Query("DELETE FROM channel_scroll_state")
    abstract suspend fun clearAllScrollStates()

    @Transaction
    open suspend fun clearAllHistory() {
        clearAllMessages()
        clearAllModerationActions()
        clearAllAttentionEntries()
        clearAllScrollStates()
        deleteOrphanUsers()
    }

    @Query("SELECT COUNT(*) FROM chat_messages")
    abstract suspend fun countMessages(): Int

    @Query("DELETE FROM chat_messages WHERE timestamp_millis < :cutoffMillis")
    abstract suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM moderation_actions WHERE created_at_millis < :cutoffMillis")
    abstract suspend fun deleteModerationActionsOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM attention_entries WHERE timestamp_millis < :cutoffMillis")
    abstract suspend fun deleteAttentionEntriesOlderThan(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM chat_messages
        WHERE id IN (
            SELECT id FROM chat_messages
            ORDER BY timestamp_millis ASC, id ASC
            LIMIT :limit
        )
        """,
    )
    abstract suspend fun deleteOldestMessages(limit: Int): Int

    @Query(
        """
        DELETE FROM moderation_actions
        WHERE id IN (
            SELECT id FROM moderation_actions
            ORDER BY created_at_millis ASC, id ASC
            LIMIT :limit
        )
        """,
    )
    abstract suspend fun deleteOldestModerationActions(limit: Int): Int

    @Query(
        """
        DELETE FROM attention_entries
        WHERE message_id IN (
            SELECT message_id FROM attention_entries
            ORDER BY timestamp_millis ASC, message_id ASC
            LIMIT :limit
        )
        """,
    )
    abstract suspend fun deleteOldestAttentionEntries(limit: Int): Int

    @Query("DELETE FROM users WHERE id NOT IN (SELECT DISTINCT author_id FROM chat_messages)")
    abstract suspend fun deleteOrphanUsers()

    @Query(
        """
        DELETE FROM chat_messages
        WHERE channel_id = :channelId
          AND id NOT IN (
              SELECT id FROM chat_messages
              WHERE channel_id = :channelId
              ORDER BY timestamp_millis DESC, id DESC
              LIMIT :limit
          )
        """,
    )
    abstract suspend fun trimChannel(channelId: String, limit: Int)
}
