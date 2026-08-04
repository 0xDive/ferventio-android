package io.ferventio.app.data.local

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatNotice
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatLinkParser
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.ReplyContext
import io.ferventio.app.domain.ReplyTextNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object ChatHistoryMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toChannelEntity(channel: ChatChannel, nowMillis: Long): ChannelEntity =
        ChannelEntity(
            id = channel.id,
            login = channel.login,
            displayName = channel.displayName,
            profileImageUrl = channel.profileImageUrl,
            updatedAtMillis = nowMillis,
        )

    fun toWriteBundle(message: ChatMessage, nowMillis: Long): MessageWriteBundle =
        MessageWriteBundle(
            channel = ChannelEntity(
                id = message.channelId,
                login = message.channelLogin,
                displayName = message.channelLogin.ifBlank { message.channelId },
                profileImageUrl = null,
                updatedAtMillis = nowMillis,
            ),
            user = UserEntity(
                id = message.author.id,
                login = message.author.login,
                displayName = message.author.displayName,
                color = message.author.color,
                profileImageUrl = message.author.profileImageUrl,
                updatedAtMillis = nowMillis,
            ),
            message = ChatMessageEntity(
                id = message.id,
                eventSubMessageId = message.eventSubMessageId,
                channelId = message.channelId,
                channelLogin = message.channelLogin,
                authorId = message.author.id,
                authorLogin = message.author.login,
                authorDisplayName = message.author.displayName,
                authorColor = message.author.color,
                text = message.text,
                timestamp = message.timestamp,
                timestampMillis = message.timestampMillis,
                messageType = message.type.name,
                isDeleted = message.flags.isDeleted,
                isSystem = message.flags.isSystem,
                isAction = message.flags.isAction,
                isFirstMessage = message.flags.isFirstMessage,
                isReturningChatter = message.flags.isReturningChatter,
                replyParentMessageId = message.reply?.parentMessageId,
                replyParentMessageBody = message.reply?.parentMessageBody,
                replyParentUserId = message.reply?.parentUserId,
                replyParentUserLogin = message.reply?.parentUserLogin,
                replyParentUserName = message.reply?.parentUserName,
                replyThreadMessageId = message.reply?.threadMessageId,
                replyThreadUserId = message.reply?.threadUserId,
                replyThreadUserLogin = message.reply?.threadUserLogin,
                replyThreadUserName = message.reply?.threadUserName,
                moderationAction = message.moderation.action?.name,
                moderationActorUserId = message.moderation.actorUserId,
                moderationReason = message.moderation.reason,
                moderationAtMillis = message.moderation.atMillis,
                noticeJson = message.notice?.toJson(),
                rewardId = message.reward?.id,
                rewardTitle = message.reward?.title,
                rewardCost = message.reward?.cost,
            ),
            badges = message.author.badges.mapIndexed { index, badge ->
                ChatBadgeEntity(
                    messageId = message.id,
                    position = index,
                    setId = badge.setId,
                    badgeId = badge.id,
                    info = badge.info,
                )
            },
            fragments = message.fragments.mapIndexed { index, fragment ->
                fragment.toEntity(message.id, index)
            },
        )

    fun fromDetails(details: MessageWithDetails): ChatMessage {
        val message = details.message
        val badges = details.badges
            .sortedBy(ChatBadgeEntity::position)
            .map { badge ->
                ChatBadge(
                    setId = badge.setId,
                    id = badge.badgeId,
                    info = badge.info,
                )
            }
        val fragments = details.fragments
            .sortedBy(ChatFragmentEntity::position)
            .flatMap(ChatHistoryMapper::fragmentsFromEntity)
            .ifEmpty { listOf(ChatFragment.Text(message.text)) }
        val reply = message.replyParentMessageId?.let { parentId ->
            ReplyContext(
                parentMessageId = parentId,
                parentMessageBody = message.replyParentMessageBody,
                parentUserId = message.replyParentUserId,
                parentUserLogin = message.replyParentUserLogin,
                parentUserName = message.replyParentUserName,
                threadMessageId = message.replyThreadMessageId,
                threadUserId = message.replyThreadUserId,
                threadUserLogin = message.replyThreadUserLogin,
                threadUserName = message.replyThreadUserName,
            )
        }
        val normalizedContent = ReplyTextNormalizer.normalize(
            text = message.text,
            fragments = fragments,
            reply = reply,
        )
        val storedUser = details.user

        return ChatMessage(
            id = message.id,
            eventSubMessageId = message.eventSubMessageId,
            channelId = message.channelId,
            channelLogin = message.channelLogin,
            author = ChatAuthor(
                id = message.authorId,
                login = storedUser?.login ?: message.authorLogin,
                displayName = storedUser?.displayName ?: message.authorDisplayName,
                color = storedUser?.color?.takeIf(String::isNotBlank) ?: message.authorColor,
                badges = badges,
                profileImageUrl = storedUser?.profileImageUrl,
            ),
            text = normalizedContent.text,
            fragments = normalizedContent.fragments,
            timestamp = message.timestamp,
            timestampMillis = message.timestampMillis,
            reply = reply,
            notice = message.noticeJson?.toNotice(),
            reward = message.rewardId?.let { rewardId ->
                ChatReward(
                    id = rewardId,
                    title = message.rewardTitle,
                    cost = message.rewardCost,
                )
            },
            type = message.messageType.toMessageType(),
            flags = MessageFlags(
                isDeleted = message.isDeleted,
                isSystem = message.isSystem,
                isAction = message.isAction,
                isFirstMessage = message.isFirstMessage,
                isReturningChatter = message.isReturningChatter,
            ),
            moderation = ModerationState(
                action = message.moderationAction?.let { value ->
                    runCatching { ModerationAction.valueOf(value) }.getOrNull()
                },
                actorUserId = message.moderationActorUserId,
                reason = message.moderationReason,
                atMillis = message.moderationAtMillis,
            ),
        )
    }

    private fun ChatFragment.toEntity(messageId: String, position: Int): ChatFragmentEntity =
        when (this) {
            is ChatFragment.Text -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "TEXT",
                text = text,
                emoteId = null,
                emoteSetId = null,
                ownerId = null,
                formats = null,
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = null,
                rawType = null,
            )

            is ChatFragment.TwitchEmote -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "TWITCH_EMOTE",
                text = text,
                emoteId = emoteId,
                emoteSetId = emoteSetId,
                ownerId = ownerId,
                formats = formats.sorted().joinToString(","),
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = null,
                rawType = null,
            )

            is ChatFragment.ThirdPartyEmote -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "THIRD_PARTY_EMOTE",
                text = text,
                emoteId = emoteId,
                emoteSetId = null,
                ownerId = null,
                formats = buildList {
                    add(if (animated) "animated" else "static")
                    if (zeroWidth) add("zero_width")
                }.joinToString(","),
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = imageUrl,
                rawType = provider,
            )

            is ChatFragment.Gif -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "GIF",
                text = text,
                emoteId = gifId,
                emoteSetId = null,
                ownerId = null,
                formats = "animated",
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = url,
                rawType = "gif",
            )

            is ChatFragment.Mention -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "MENTION",
                text = text,
                emoteId = null,
                emoteSetId = null,
                ownerId = null,
                formats = null,
                mentionUserId = userId,
                mentionUserLogin = userLogin,
                mentionUserName = userName,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = null,
                rawType = null,
            )

            is ChatFragment.Cheermote -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "CHEERMOTE",
                text = text,
                emoteId = null,
                emoteSetId = null,
                ownerId = null,
                formats = null,
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = prefix,
                cheermoteBits = bits,
                cheermoteTier = tier,
                url = null,
                rawType = null,
            )

            is ChatFragment.Link -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "LINK",
                text = text,
                emoteId = null,
                emoteSetId = null,
                ownerId = null,
                formats = null,
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = url,
                rawType = null,
            )

            is ChatFragment.Unknown -> ChatFragmentEntity(
                messageId = messageId,
                position = position,
                type = "UNKNOWN",
                text = text,
                emoteId = null,
                emoteSetId = null,
                ownerId = null,
                formats = null,
                mentionUserId = null,
                mentionUserLogin = null,
                mentionUserName = null,
                cheermotePrefix = null,
                cheermoteBits = null,
                cheermoteTier = null,
                url = null,
                rawType = rawType,
            )
        }

    private fun fragmentsFromEntity(entity: ChatFragmentEntity): List<ChatFragment> {
        if (entity.type != "LINK") return listOf(fragmentFromEntity(entity))

        val matches = ChatLinkParser.findAll(entity.text)
        if (matches.isEmpty()) {
            val fallback = ChatLinkParser.normalize(entity.url ?: entity.text)
            return if (fallback != null && entity.text.isNotBlank()) {
                listOf(ChatFragment.Link(text = entity.text, url = fallback))
            } else {
                listOf(ChatFragment.Text(entity.text))
            }
        }

        return buildList {
            var cursor = 0
            matches.forEach { match ->
                if (match.start > cursor) {
                    add(ChatFragment.Text(entity.text.substring(cursor, match.start)))
                }
                add(
                    ChatFragment.Link(
                        text = entity.text.substring(match.start, match.endExclusive),
                        url = match.url,
                    ),
                )
                cursor = match.endExclusive
            }
            if (cursor < entity.text.length) {
                add(ChatFragment.Text(entity.text.substring(cursor)))
            }
        }
    }

    private fun fragmentFromEntity(entity: ChatFragmentEntity): ChatFragment =
        when (entity.type) {
            "TEXT" -> ChatFragment.Text(entity.text)
            "TWITCH_EMOTE" -> ChatFragment.TwitchEmote(
                text = entity.text,
                emoteId = entity.emoteId.orEmpty(),
                emoteSetId = entity.emoteSetId,
                ownerId = entity.ownerId,
                formats = entity.formats
                    .orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
            )

            "THIRD_PARTY_EMOTE" -> ChatFragment.ThirdPartyEmote(
                text = entity.text,
                emoteId = entity.emoteId.orEmpty(),
                provider = entity.rawType.orEmpty(),
                animated = entity.formats.orEmpty().split(',').contains("animated"),
                imageUrl = entity.url,
                zeroWidth = entity.formats.orEmpty().split(',').contains("zero_width"),
            )

            "GIF" -> ChatFragment.Gif(
                text = entity.text,
                gifId = entity.emoteId.orEmpty(),
                url = entity.url.orEmpty(),
            )

            "MENTION" -> ChatFragment.Mention(
                text = entity.text,
                userId = entity.mentionUserId.orEmpty(),
                userLogin = entity.mentionUserLogin.orEmpty(),
                userName = entity.mentionUserName.orEmpty(),
            )

            "CHEERMOTE" -> ChatFragment.Cheermote(
                text = entity.text,
                prefix = entity.cheermotePrefix.orEmpty(),
                bits = entity.cheermoteBits ?: 0,
                tier = entity.cheermoteTier ?: 0,
            )

            "LINK" -> ChatFragment.Link(
                text = entity.text,
                url = entity.url ?: entity.text,
            )

            else -> ChatFragment.Unknown(
                text = entity.text,
                rawType = entity.rawType ?: entity.type,
            )
        }

    private fun String.toMessageType(): ChatMessageType =
        runCatching { ChatMessageType.valueOf(this) }.getOrDefault(ChatMessageType.UNKNOWN)

    private fun ChatNotice.toJson(): String = buildJsonObject {
        put("type", JsonPrimitive(type))
        systemMessage?.let { put("system_message", JsonPrimitive(it)) }
        userMessage?.let { put("user_message", JsonPrimitive(it)) }
        subTier?.let { put("sub_tier", JsonPrimitive(it)) }
        isPrime?.let { put("is_prime", JsonPrimitive(it)) }
        durationMonths?.let { put("duration_months", JsonPrimitive(it)) }
        cumulativeMonths?.let { put("cumulative_months", JsonPrimitive(it)) }
        streakMonths?.let { put("streak_months", JsonPrimitive(it)) }
        isGift?.let { put("is_gift", JsonPrimitive(it)) }
        giftTotal?.let { put("gift_total", JsonPrimitive(it)) }
        cumulativeGiftTotal?.let { put("cumulative_gift_total", JsonPrimitive(it)) }
        communityGiftId?.let { put("community_gift_id", JsonPrimitive(it)) }
        gifterIsAnonymous?.let { put("gifter_is_anonymous", JsonPrimitive(it)) }
        gifterUserId?.let { put("gifter_user_id", JsonPrimitive(it)) }
        gifterUserLogin?.let { put("gifter_user_login", JsonPrimitive(it)) }
        gifterUserName?.let { put("gifter_user_name", JsonPrimitive(it)) }
        recipientUserId?.let { put("recipient_user_id", JsonPrimitive(it)) }
        recipientUserLogin?.let { put("recipient_user_login", JsonPrimitive(it)) }
        recipientUserName?.let { put("recipient_user_name", JsonPrimitive(it)) }
        raidUserId?.let { put("raid_user_id", JsonPrimitive(it)) }
        raidUserLogin?.let { put("raid_user_login", JsonPrimitive(it)) }
        raidUserName?.let { put("raid_user_name", JsonPrimitive(it)) }
        raidViewerCount?.let { put("raid_viewer_count", JsonPrimitive(it)) }
        raidProfileImageUrl?.let { put("raid_profile_image_url", JsonPrimitive(it)) }
        announcementColor?.let { put("announcement_color", JsonPrimitive(it)) }
        put("is_anonymous", JsonPrimitive(isAnonymous))
    }.toString()

    private fun String.toNotice(): ChatNotice? = runCatching {
        val root = json.parseToJsonElement(this) as? JsonObject ?: return@runCatching null
        ChatNotice(
            type = root.string("type").orEmpty(),
            systemMessage = root.string("system_message"),
            userMessage = root.string("user_message"),
            subTier = root.string("sub_tier"),
            isPrime = root.boolean("is_prime"),
            durationMonths = root.int("duration_months"),
            cumulativeMonths = root.int("cumulative_months"),
            streakMonths = root.int("streak_months"),
            isGift = root.boolean("is_gift"),
            giftTotal = root.int("gift_total"),
            cumulativeGiftTotal = root.int("cumulative_gift_total"),
            communityGiftId = root.string("community_gift_id"),
            gifterIsAnonymous = root.boolean("gifter_is_anonymous"),
            gifterUserId = root.string("gifter_user_id"),
            gifterUserLogin = root.string("gifter_user_login"),
            gifterUserName = root.string("gifter_user_name"),
            recipientUserId = root.string("recipient_user_id"),
            recipientUserLogin = root.string("recipient_user_login"),
            recipientUserName = root.string("recipient_user_name"),
            raidUserId = root.string("raid_user_id"),
            raidUserLogin = root.string("raid_user_login"),
            raidUserName = root.string("raid_user_name"),
            raidViewerCount = root.int("raid_viewer_count"),
            raidProfileImageUrl = root.string("raid_profile_image_url"),
            announcementColor = root.string("announcement_color"),
            isAnonymous = root.boolean("is_anonymous") ?: false,
        )
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

}
