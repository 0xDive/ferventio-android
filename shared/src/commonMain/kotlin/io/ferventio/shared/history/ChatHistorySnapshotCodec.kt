package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatNotice
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.ReplyContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object ChatHistorySnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(messages: List<ChatMessage>): String =
        json.encodeToString(ChatHistorySnapshot(messages = messages.map(ChatMessage::toRecord)))

    fun decode(value: String): List<ChatMessage> =
        json.decodeFromString<ChatHistorySnapshot>(value).messages.map(ChatHistoryMessageRecord::toDomain)

    fun decodeOrEmpty(value: String?): List<ChatMessage> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { decode(value) }.getOrDefault(emptyList())
    }
}

@Serializable
private data class ChatHistorySnapshot(
    val version: Int = CURRENT_VERSION,
    val messages: List<ChatHistoryMessageRecord> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
private data class ChatHistoryMessageRecord(
    val id: String,
    val eventSubMessageId: String? = null,
    val channelId: String,
    val channelLogin: String,
    val authorId: String,
    val authorLogin: String,
    val authorDisplayName: String,
    val authorColor: String? = null,
    val authorProfileImageUrl: String? = null,
    val text: String,
    val timestamp: String,
    val timestampMillis: Long,
    val type: String,
    val isDeleted: Boolean = false,
    val isSystem: Boolean = false,
    val isAction: Boolean = false,
    val isFirstMessage: Boolean = false,
    val isReturningChatter: Boolean = false,
    val moderationAction: String? = null,
    val moderationActorUserId: String? = null,
    val moderationReason: String? = null,
    val moderationAtMillis: Long? = null,
    val reply: ChatHistoryReplyRecord? = null,
    val notice: ChatHistoryNoticeRecord? = null,
    val reward: ChatHistoryRewardRecord? = null,
    val badges: List<ChatHistoryBadgeRecord> = emptyList(),
    val fragments: List<ChatHistoryFragmentRecord> = emptyList(),
)

@Serializable
private data class ChatHistoryBadgeRecord(
    val setId: String,
    val id: String,
    val info: String? = null,
)

@Serializable
private data class ChatHistoryReplyRecord(
    val parentMessageId: String,
    val parentMessageBody: String? = null,
    val parentUserId: String? = null,
    val parentUserLogin: String? = null,
    val parentUserName: String? = null,
    val threadMessageId: String? = null,
    val threadUserId: String? = null,
    val threadUserLogin: String? = null,
    val threadUserName: String? = null,
)

@Serializable
private data class ChatHistoryNoticeRecord(
    val type: String,
    val systemMessage: String? = null,
    val userMessage: String? = null,
    val subTier: String? = null,
    val isPrime: Boolean? = null,
    val durationMonths: Int? = null,
    val cumulativeMonths: Int? = null,
    val streakMonths: Int? = null,
    val isGift: Boolean? = null,
    val giftTotal: Int? = null,
    val cumulativeGiftTotal: Int? = null,
    val communityGiftId: String? = null,
    val gifterIsAnonymous: Boolean? = null,
    val gifterUserId: String? = null,
    val gifterUserLogin: String? = null,
    val gifterUserName: String? = null,
    val recipientUserId: String? = null,
    val recipientUserLogin: String? = null,
    val recipientUserName: String? = null,
    val raidUserId: String? = null,
    val raidUserLogin: String? = null,
    val raidUserName: String? = null,
    val raidViewerCount: Int? = null,
    val raidProfileImageUrl: String? = null,
    val announcementColor: String? = null,
    val isAnonymous: Boolean = false,
)

@Serializable
private data class ChatHistoryRewardRecord(
    val id: String? = null,
    val title: String? = null,
    val cost: Long? = null,
)

@Serializable
private data class ChatHistoryFragmentRecord(
    val type: String,
    val text: String,
    val emoteId: String? = null,
    val emoteSetId: String? = null,
    val ownerId: String? = null,
    val formats: List<String> = emptyList(),
    val provider: String? = null,
    val animated: Boolean = false,
    val imageUrl: String? = null,
    val zeroWidth: Boolean = false,
    val gifId: String? = null,
    val url: String? = null,
    val mentionUserId: String? = null,
    val mentionUserLogin: String? = null,
    val mentionUserName: String? = null,
    val cheermotePrefix: String? = null,
    val cheermoteBits: Int? = null,
    val cheermoteTier: Int? = null,
    val rawType: String? = null,
)

private fun ChatMessage.toRecord(): ChatHistoryMessageRecord = ChatHistoryMessageRecord(
    id = id,
    eventSubMessageId = eventSubMessageId,
    channelId = channelId,
    channelLogin = channelLogin,
    authorId = author.id,
    authorLogin = author.login,
    authorDisplayName = author.displayName,
    authorColor = author.color,
    authorProfileImageUrl = author.profileImageUrl,
    text = text,
    timestamp = timestamp,
    timestampMillis = timestampMillis,
    type = type.name,
    isDeleted = flags.isDeleted,
    isSystem = flags.isSystem,
    isAction = flags.isAction,
    isFirstMessage = flags.isFirstMessage,
    isReturningChatter = flags.isReturningChatter,
    moderationAction = moderation.action?.name,
    moderationActorUserId = moderation.actorUserId,
    moderationReason = moderation.reason,
    moderationAtMillis = moderation.atMillis,
    reply = reply?.let { value ->
        ChatHistoryReplyRecord(
            parentMessageId = value.parentMessageId,
            parentMessageBody = value.parentMessageBody,
            parentUserId = value.parentUserId,
            parentUserLogin = value.parentUserLogin,
            parentUserName = value.parentUserName,
            threadMessageId = value.threadMessageId,
            threadUserId = value.threadUserId,
            threadUserLogin = value.threadUserLogin,
            threadUserName = value.threadUserName,
        )
    },
    notice = notice?.let { value ->
        ChatHistoryNoticeRecord(
            type = value.type,
            systemMessage = value.systemMessage,
            userMessage = value.userMessage,
            subTier = value.subTier,
            isPrime = value.isPrime,
            durationMonths = value.durationMonths,
            cumulativeMonths = value.cumulativeMonths,
            streakMonths = value.streakMonths,
            isGift = value.isGift,
            giftTotal = value.giftTotal,
            cumulativeGiftTotal = value.cumulativeGiftTotal,
            communityGiftId = value.communityGiftId,
            gifterIsAnonymous = value.gifterIsAnonymous,
            gifterUserId = value.gifterUserId,
            gifterUserLogin = value.gifterUserLogin,
            gifterUserName = value.gifterUserName,
            recipientUserId = value.recipientUserId,
            recipientUserLogin = value.recipientUserLogin,
            recipientUserName = value.recipientUserName,
            raidUserId = value.raidUserId,
            raidUserLogin = value.raidUserLogin,
            raidUserName = value.raidUserName,
            raidViewerCount = value.raidViewerCount,
            raidProfileImageUrl = value.raidProfileImageUrl,
            announcementColor = value.announcementColor,
            isAnonymous = value.isAnonymous,
        )
    },
    reward = reward?.let { ChatHistoryRewardRecord(it.id, it.title, it.cost) },
    badges = author.badges.map { ChatHistoryBadgeRecord(it.setId, it.id, it.info) },
    fragments = fragments.map(ChatFragment::toRecord),
)

private fun ChatHistoryMessageRecord.toDomain(): ChatMessage = ChatMessage(
    id = id,
    eventSubMessageId = eventSubMessageId,
    channelId = channelId,
    channelLogin = channelLogin,
    author = ChatAuthor(
        id = authorId,
        login = authorLogin,
        displayName = authorDisplayName,
        color = authorColor,
        badges = badges.map { ChatBadge(setId = it.setId, id = it.id, info = it.info) },
        profileImageUrl = authorProfileImageUrl,
    ),
    text = text,
    fragments = fragments.map(ChatHistoryFragmentRecord::toDomain).ifEmpty { listOf(ChatFragment.Text(text)) },
    timestamp = timestamp,
    timestampMillis = timestampMillis,
    reply = reply?.let { value ->
        ReplyContext(
            parentMessageId = value.parentMessageId,
            parentMessageBody = value.parentMessageBody,
            parentUserId = value.parentUserId,
            parentUserLogin = value.parentUserLogin,
            parentUserName = value.parentUserName,
            threadMessageId = value.threadMessageId,
            threadUserId = value.threadUserId,
            threadUserLogin = value.threadUserLogin,
            threadUserName = value.threadUserName,
        )
    },
    notice = notice?.let { value ->
        ChatNotice(
            type = value.type,
            systemMessage = value.systemMessage,
            userMessage = value.userMessage,
            subTier = value.subTier,
            isPrime = value.isPrime,
            durationMonths = value.durationMonths,
            cumulativeMonths = value.cumulativeMonths,
            streakMonths = value.streakMonths,
            isGift = value.isGift,
            giftTotal = value.giftTotal,
            cumulativeGiftTotal = value.cumulativeGiftTotal,
            communityGiftId = value.communityGiftId,
            gifterIsAnonymous = value.gifterIsAnonymous,
            gifterUserId = value.gifterUserId,
            gifterUserLogin = value.gifterUserLogin,
            gifterUserName = value.gifterUserName,
            recipientUserId = value.recipientUserId,
            recipientUserLogin = value.recipientUserLogin,
            recipientUserName = value.recipientUserName,
            raidUserId = value.raidUserId,
            raidUserLogin = value.raidUserLogin,
            raidUserName = value.raidUserName,
            raidViewerCount = value.raidViewerCount,
            raidProfileImageUrl = value.raidProfileImageUrl,
            announcementColor = value.announcementColor,
            isAnonymous = value.isAnonymous,
        )
    },
    reward = reward?.let { ChatReward(id = it.id, title = it.title, cost = it.cost) },
    type = runCatching { ChatMessageType.valueOf(type) }.getOrDefault(ChatMessageType.UNKNOWN),
    flags = MessageFlags(
        isDeleted = isDeleted,
        isSystem = isSystem,
        isAction = isAction,
        isFirstMessage = isFirstMessage,
        isReturningChatter = isReturningChatter,
    ),
    moderation = ModerationState(
        action = moderationAction?.let { value -> runCatching { ModerationAction.valueOf(value) }.getOrNull() },
        actorUserId = moderationActorUserId,
        reason = moderationReason,
        atMillis = moderationAtMillis,
    ),
)

private fun ChatFragment.toRecord(): ChatHistoryFragmentRecord = when (this) {
    is ChatFragment.Text -> ChatHistoryFragmentRecord(type = "TEXT", text = text)
    is ChatFragment.TwitchEmote -> ChatHistoryFragmentRecord(
        type = "TWITCH_EMOTE",
        text = text,
        emoteId = emoteId,
        emoteSetId = emoteSetId,
        ownerId = ownerId,
        formats = formats.sorted(),
    )
    is ChatFragment.ThirdPartyEmote -> ChatHistoryFragmentRecord(
        type = "THIRD_PARTY_EMOTE",
        text = text,
        emoteId = emoteId,
        provider = provider,
        animated = animated,
        imageUrl = imageUrl,
        zeroWidth = zeroWidth,
    )
    is ChatFragment.Gif -> ChatHistoryFragmentRecord(
        type = "GIF",
        text = text,
        gifId = gifId,
        url = url,
    )
    is ChatFragment.Mention -> ChatHistoryFragmentRecord(
        type = "MENTION",
        text = text,
        mentionUserId = userId,
        mentionUserLogin = userLogin,
        mentionUserName = userName,
    )
    is ChatFragment.Cheermote -> ChatHistoryFragmentRecord(
        type = "CHEERMOTE",
        text = text,
        cheermotePrefix = prefix,
        cheermoteBits = bits,
        cheermoteTier = tier,
    )
    is ChatFragment.Link -> ChatHistoryFragmentRecord(type = "LINK", text = text, url = url)
    is ChatFragment.Unknown -> ChatHistoryFragmentRecord(type = "UNKNOWN", text = text, rawType = rawType)
}

private fun ChatHistoryFragmentRecord.toDomain(): ChatFragment = when (type) {
    "TEXT" -> ChatFragment.Text(text)
    "TWITCH_EMOTE" -> ChatFragment.TwitchEmote(
        text = text,
        emoteId = emoteId.orEmpty(),
        emoteSetId = emoteSetId,
        ownerId = ownerId,
        formats = formats.toSet(),
    )
    "THIRD_PARTY_EMOTE" -> ChatFragment.ThirdPartyEmote(
        text = text,
        emoteId = emoteId.orEmpty(),
        provider = provider.orEmpty(),
        animated = animated,
        imageUrl = imageUrl,
        zeroWidth = zeroWidth,
    )
    "GIF" -> ChatFragment.Gif(text = text, gifId = gifId.orEmpty(), url = url.orEmpty())
    "MENTION" -> ChatFragment.Mention(
        text = text,
        userId = mentionUserId.orEmpty(),
        userLogin = mentionUserLogin.orEmpty(),
        userName = mentionUserName.orEmpty(),
    )
    "CHEERMOTE" -> ChatFragment.Cheermote(
        text = text,
        prefix = cheermotePrefix.orEmpty(),
        bits = cheermoteBits ?: 0,
        tier = cheermoteTier ?: 0,
    )
    "LINK" -> ChatFragment.Link(text = text, url = url.orEmpty())
    "UNKNOWN" -> ChatFragment.Unknown(text = text, rawType = rawType.orEmpty())
    else -> ChatFragment.Unknown(text = text, rawType = rawType ?: type)
}
