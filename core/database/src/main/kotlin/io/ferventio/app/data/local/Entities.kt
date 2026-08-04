package io.ferventio.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "channels",
    indices = [Index(value = ["login"], unique = true)],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val login: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "profile_image_url") val profileImageUrl: String?,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)

@Entity(
    tableName = "users",
    indices = [Index(value = ["login"])],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val login: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val color: String?,
    @ColumnInfo(name = "profile_image_url") val profileImageUrl: String?,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["channel_id", "timestamp_millis"]),
        Index(value = ["channel_id", "timestamp_millis", "id"]),
        Index(value = ["channel_id", "author_id", "timestamp_millis"]),
        Index(value = ["channel_login", "timestamp_millis"]),
        Index(value = ["author_id", "timestamp_millis"]),
        Index(value = ["author_login", "timestamp_millis"]),
        Index(value = ["message_type", "timestamp_millis"]),
        Index(value = ["is_deleted", "timestamp_millis"]),
        Index(value = ["moderation_action", "timestamp_millis"]),
        Index(value = ["timestamp_millis"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "eventsub_message_id") val eventSubMessageId: String?,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "channel_login") val channelLogin: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "author_login") val authorLogin: String,
    @ColumnInfo(name = "author_display_name") val authorDisplayName: String,
    @ColumnInfo(name = "author_color") val authorColor: String?,
    val text: String,
    val timestamp: String,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    @ColumnInfo(name = "message_type") val messageType: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    @ColumnInfo(name = "is_action") val isAction: Boolean,
    @ColumnInfo(name = "is_first_message") val isFirstMessage: Boolean,
    @ColumnInfo(name = "is_returning_chatter") val isReturningChatter: Boolean,
    @ColumnInfo(name = "reply_parent_message_id") val replyParentMessageId: String?,
    @ColumnInfo(name = "reply_parent_message_body") val replyParentMessageBody: String?,
    @ColumnInfo(name = "reply_parent_user_id") val replyParentUserId: String?,
    @ColumnInfo(name = "reply_parent_user_login") val replyParentUserLogin: String?,
    @ColumnInfo(name = "reply_parent_user_name") val replyParentUserName: String?,
    @ColumnInfo(name = "reply_thread_message_id") val replyThreadMessageId: String?,
    @ColumnInfo(name = "reply_thread_user_id") val replyThreadUserId: String?,
    @ColumnInfo(name = "reply_thread_user_login") val replyThreadUserLogin: String?,
    @ColumnInfo(name = "reply_thread_user_name") val replyThreadUserName: String?,
    @ColumnInfo(name = "moderation_action") val moderationAction: String?,
    @ColumnInfo(name = "moderation_actor_user_id") val moderationActorUserId: String?,
    @ColumnInfo(name = "moderation_reason") val moderationReason: String?,
    @ColumnInfo(name = "moderation_at_millis") val moderationAtMillis: Long?,
    @ColumnInfo(name = "notice_json") val noticeJson: String?,
    @ColumnInfo(name = "reward_id") val rewardId: String?,
    @ColumnInfo(name = "reward_title") val rewardTitle: String?,
    @ColumnInfo(name = "reward_cost") val rewardCost: Long?,
)


@Entity(tableName = "channel_scroll_state")
data class ChannelScrollStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "anchor_message_id")
    val anchorMessageId: String?,
    @ColumnInfo(name = "first_visible_item_index")
    val firstVisibleItemIndex: Int,
    @ColumnInfo(name = "first_visible_item_scroll_offset")
    val firstVisibleItemScrollOffset: Int,
    @ColumnInfo(name = "is_at_bottom")
    val isAtBottom: Boolean,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_badges",
    primaryKeys = ["message_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["message_id"])],
)
data class ChatBadgeEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    val position: Int,
    @ColumnInfo(name = "set_id") val setId: String,
    @ColumnInfo(name = "badge_id") val badgeId: String,
    val info: String?,
)

@Entity(
    tableName = "chat_fragments",
    primaryKeys = ["message_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["message_id"])],
)
data class ChatFragmentEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    val position: Int,
    val type: String,
    val text: String,
    @ColumnInfo(name = "emote_id") val emoteId: String?,
    @ColumnInfo(name = "emote_set_id") val emoteSetId: String?,
    @ColumnInfo(name = "owner_id") val ownerId: String?,
    val formats: String?,
    @ColumnInfo(name = "mention_user_id") val mentionUserId: String?,
    @ColumnInfo(name = "mention_user_login") val mentionUserLogin: String?,
    @ColumnInfo(name = "mention_user_name") val mentionUserName: String?,
    @ColumnInfo(name = "cheermote_prefix") val cheermotePrefix: String?,
    @ColumnInfo(name = "cheermote_bits") val cheermoteBits: Int?,
    @ColumnInfo(name = "cheermote_tier") val cheermoteTier: Int?,
    val url: String?,
    @ColumnInfo(name = "raw_type") val rawType: String?,
)


@Entity(
    tableName = "moderation_actions",
    indices = [
        Index(value = ["channel_id", "created_at_millis"]),
        Index(value = ["target_user_id", "created_at_millis"]),
    ],
)
data class ModerationActionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "target_user_id") val targetUserId: String?,
    @ColumnInfo(name = "target_user_login") val targetUserLogin: String?,
    @ColumnInfo(name = "message_id") val messageId: String?,
    val action: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    val reason: String?,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

@Entity(
    tableName = "attention_entries",
    indices = [
        Index(value = ["is_read", "timestamp_millis"]),
        Index(value = ["channel_id", "timestamp_millis"]),
    ],
)
data class AttentionEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "channel_login")
    val channelLogin: String,
    @ColumnInfo(name = "author_id")
    val authorId: String,
    @ColumnInfo(name = "author_login")
    val authorLogin: String,
    @ColumnInfo(name = "author_display_name")
    val authorDisplayName: String,
    val text: String,
    val timestamp: String,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean,
    @ColumnInfo(name = "is_direct_mention")
    val isDirectMention: Boolean,
    @ColumnInfo(name = "is_highlight")
    val isHighlight: Boolean,
    @ColumnInfo(name = "highlight_reasons_json")
    val highlightReasonsJson: String,
    @ColumnInfo(name = "highlight_color_argb")
    val highlightColorArgb: Long?,
)

data class MessageWithDetails(
    @Embedded val message: ChatMessageEntity,
    @Relation(
        parentColumn = "author_id",
        entityColumn = "id",
    )
    val user: UserEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id",
    )
    val badges: List<ChatBadgeEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id",
    )
    val fragments: List<ChatFragmentEntity>,
)

data class MessageWriteBundle(
    val channel: ChannelEntity,
    val user: UserEntity,
    val message: ChatMessageEntity,
    val badges: List<ChatBadgeEntity>,
    val fragments: List<ChatFragmentEntity>,
)
