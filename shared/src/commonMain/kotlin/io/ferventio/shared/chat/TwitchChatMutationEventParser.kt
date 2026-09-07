package io.ferventio.shared.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal sealed interface TwitchChatMutationEvent {
    val channelId: String
    val eventSubMessageId: String?
    val createdAt: String?

    data class MessageDeleted(
        override val channelId: String,
        val messageId: String,
        override val eventSubMessageId: String?,
        override val createdAt: String?,
    ) : TwitchChatMutationEvent

    data class UserMessagesCleared(
        override val channelId: String,
        val userId: String,
        val userLogin: String?,
        override val eventSubMessageId: String?,
        override val createdAt: String?,
    ) : TwitchChatMutationEvent

    data class ChatCleared(
        override val channelId: String,
        override val eventSubMessageId: String?,
        override val createdAt: String?,
    ) : TwitchChatMutationEvent
}

/**
 * Parses the canonical message-removal EventSub notifications shared by Android and iOS.
 * System notices/history side effects deliberately stay outside this parser.
 */
internal object TwitchChatMutationEventParser {
    fun parse(envelope: TwitchEventSubProtocolEnvelope): TwitchChatMutationEvent? {
        if (envelope.type != "notification") return null
        val event = envelope.eventPayload ?: return null
        val channelId = event.requiredString("broadcaster_user_id")
        val eventSubMessageId = envelope.messageId?.takeIf(String::isNotBlank)
        val createdAt = envelope.messageTimestamp?.takeIf(String::isNotBlank)

        return when (envelope.subscriptionType) {
            "channel.chat.message_delete" -> TwitchChatMutationEvent.MessageDeleted(
                channelId = channelId,
                messageId = event.requiredString("message_id"),
                eventSubMessageId = eventSubMessageId,
                createdAt = createdAt,
            )

            "channel.chat.clear_user_messages" -> TwitchChatMutationEvent.UserMessagesCleared(
                channelId = channelId,
                userId = event.requiredString("target_user_id"),
                userLogin = event.string("target_user_login"),
                eventSubMessageId = eventSubMessageId,
                createdAt = createdAt,
            )

            "channel.chat.clear" -> TwitchChatMutationEvent.ChatCleared(
                channelId = channelId,
                eventSubMessageId = eventSubMessageId,
                createdAt = createdAt,
            )

            else -> null
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Twitch EventSub event is missing $name")

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}
