package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ReplyContext
import io.ferventio.app.domain.ReplyTextNormalizer
import io.ferventio.app.domain.toEpochMillisOrNow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Maps the primary Twitch EventSub chat notification onto Ferventio's canonical message model. */
object TwitchChatMessageEventParser {
    fun parse(envelope: TwitchEventSubProtocolEnvelope): ChatMessage? {
        if (envelope.type != "notification" ||
            envelope.subscriptionType != TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE
        ) {
            return null
        }
        val event = envelope.eventPayload
            ?: throw IllegalArgumentException("channel.chat.message is missing its event payload")
        return parseEvent(
            event = event,
            timestamp = envelope.messageTimestamp.orEmpty(),
        ).copy(eventSubMessageId = envelope.messageId)
    }

    fun parseEvent(
        event: JsonObject,
        timestamp: String,
    ): ChatMessage {
        val messageObject = event.objectOrEmpty("message")
        val replyObject = event.objectOrNull("reply")
        val badges = event.arrayOrEmpty("badges")
        val messageId = event.string("message_id").orEmpty()
        val channelId = event.string("broadcaster_user_id").orEmpty()
        val userId = event.string("chatter_user_id").orEmpty()
        require(messageId.isNotBlank()) { "channel.chat.message is missing message_id" }
        require(channelId.isNotBlank()) { "channel.chat.message is missing broadcaster_user_id" }
        require(userId.isNotBlank()) { "channel.chat.message is missing chatter_user_id" }

        val reply = replyObject?.string("parent_message_id")?.let { parentMessageId ->
            ReplyContext(
                parentMessageId = parentMessageId,
                parentMessageBody = replyObject.string("parent_message_body"),
                parentUserId = replyObject.string("parent_user_id"),
                parentUserLogin = replyObject.string("parent_user_login"),
                parentUserName = replyObject.string("parent_user_name"),
                threadMessageId = replyObject.string("thread_message_id"),
                threadUserId = replyObject.string("thread_user_id"),
                threadUserLogin = replyObject.string("thread_user_login"),
                threadUserName = replyObject.string("thread_user_name"),
            )
        }
        val rawText = messageObject.string("text").orEmpty()
        val rawFragments = parseFragments(messageObject, rawText)
        val normalizedContent = ReplyTextNormalizer.normalize(
            text = rawText,
            fragments = rawFragments,
            reply = reply,
        )
        val rawMessageType = event.string("message_type").orEmpty()
        val rewardObject = event.objectOrNull("reward") ?: messageObject.objectOrNull("reward")
        val rewardId = event.string("channel_points_custom_reward_id") ?: rewardObject?.string("id")
        val reward = if (rewardId != null || rewardObject != null) {
            ChatReward(
                id = rewardId,
                title = rewardObject?.string("title"),
                cost = (rewardObject?.int("cost") ?: rewardObject?.int("channel_points"))?.toLong(),
            )
        } else {
            null
        }
        val domainType = when {
            rawMessageType == "action" -> ChatMessageType.ACTION
            rewardId != null || rewardObject != null -> ChatMessageType.REWARD
            normalizedContent.fragments.any { it is ChatFragment.Cheermote } -> ChatMessageType.CHEER
            rawMessageType in REWARD_MESSAGE_TYPES -> ChatMessageType.REWARD
            rawMessageType.isBlank() || rawMessageType == "text" || rawMessageType == "user_intro" ->
                ChatMessageType.CHAT
            else -> ChatMessageType.UNKNOWN
        }

        return ChatMessage(
            id = messageId,
            channelId = channelId,
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            author = ChatAuthor(
                id = userId,
                login = event.string("chatter_user_login").orEmpty(),
                displayName = event.string("chatter_user_name")
                    ?: event.string("chatter_user_login").orEmpty(),
                color = event.string("color"),
                badges = parseBadges(badges),
            ),
            text = normalizedContent.text,
            fragments = normalizedContent.fragments,
            timestamp = timestamp,
            timestampMillis = timestamp.toEpochMillisOrNow(),
            reply = reply,
            reward = reward,
            type = domainType,
            flags = MessageFlags(
                isAction = domainType == ChatMessageType.ACTION,
                isFirstMessage = event.boolean("first_message") ?: false,
                isReturningChatter = event.boolean("returning_chatter") ?: false,
            ),
        )
    }

    private fun parseBadges(badges: JsonArray): List<ChatBadge> =
        badges.mapNotNull { badgeElement ->
            val badge = badgeElement as? JsonObject ?: return@mapNotNull null
            ChatBadge(
                setId = badge.string("set_id").orEmpty(),
                id = badge.string("id").orEmpty(),
                info = badge.string("info"),
            )
        }

    private fun parseFragments(
        message: JsonObject,
        fallbackText: String,
    ): List<ChatFragment> {
        val parsed = message.arrayOrEmpty("fragments").mapNotNull { element ->
            val fragment = element as? JsonObject ?: return@mapNotNull null
            val type = fragment.string("type").orEmpty()
            val text = fragment.string("text").orEmpty()
            when (type) {
                "text" -> ChatFragment.Text(text)

                "emote" -> {
                    val emote = fragment.objectOrEmpty("emote")
                    ChatFragment.TwitchEmote(
                        text = text,
                        emoteId = emote.string("id").orEmpty(),
                        emoteSetId = emote.string("emote_set_id"),
                        ownerId = emote.string("owner_id"),
                        formats = emote.arrayOrEmpty("format")
                            .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
                            .toSet(),
                    )
                }

                "mention" -> {
                    val mention = fragment.objectOrEmpty("mention")
                    ChatFragment.Mention(
                        text = text,
                        userId = mention.string("user_id").orEmpty(),
                        userLogin = mention.string("user_login").orEmpty(),
                        userName = mention.string("user_name").orEmpty(),
                    )
                }

                "cheermote" -> {
                    val cheermote = fragment.objectOrEmpty("cheermote")
                    ChatFragment.Cheermote(
                        text = text,
                        prefix = cheermote.string("prefix").orEmpty(),
                        bits = cheermote.int("bits") ?: 0,
                        tier = cheermote.int("tier") ?: 0,
                    )
                }

                "gif" -> {
                    val gif = fragment.objectOrEmpty("gif")
                    ChatFragment.Gif(
                        text = text,
                        gifId = gif.string("gif_id").orEmpty(),
                        url = gif.string("url").orEmpty(),
                    )
                }

                else -> ChatFragment.Unknown(
                    text = text,
                    rawType = type.ifBlank { "unknown" },
                )
            }
        }
        return parsed.ifEmpty { listOf(ChatFragment.Text(fallbackText)) }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.objectOrEmpty(key: String): JsonObject =
        objectOrNull(key) ?: EMPTY_OBJECT

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
        this[key] as? JsonArray ?: EMPTY_ARRAY

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private val EMPTY_OBJECT = JsonObject(emptyMap())
    private val EMPTY_ARRAY = JsonArray(emptyList())
    private val REWARD_MESSAGE_TYPES = setOf(
        "channel_points_highlighted",
        "channel_points_sub_only",
        "power_ups_message_effect",
        "power_ups_gigantified_emote",
    )
}
