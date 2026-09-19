package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.InteractiveEventSubSubscriptionPolicy
import io.ferventio.app.domain.TwitchSession

data class TwitchEventSubSubscriptionSpec(
    val broadcasterId: String,
    val type: String,
    val version: String,
    val identityConditionKey: String?,
)

/**
 * Shared EventSub subscription policy matching the current Android controller.
 * Transport/retry code consumes these descriptors instead of re-encoding Twitch rules.
 */
object TwitchEventSubSubscriptionPolicy {
    const val PRIMARY_EVENT_TYPE = "channel.chat.message"
    const val NOTICE_EVENT_TYPE = "channel.chat.notification"

    val BASE_EVENT_TYPES: List<String> = listOf(
        PRIMARY_EVENT_TYPE,
        NOTICE_EVENT_TYPE,
        "channel.chat.message_delete",
        "channel.chat.clear_user_messages",
        "channel.chat.clear",
        "channel.chat_settings.update",
        "automod.message.hold",
        "automod.message.update",
        "channel.moderate",
    )

    val MODERATOR_EVENT_TYPES: Set<String> = setOf(
        "automod.message.hold",
        "automod.message.update",
        "channel.moderate",
    )

    fun subscriptionsFor(
        session: TwitchSession,
        channel: ChatChannel,
        moderatedChannelIds: Set<String>,
    ): List<TwitchEventSubSubscriptionSpec> {
        require(channel.id.isNotBlank()) { "EventSub channel id must not be blank" }
        val eventTypes = BASE_EVENT_TYPES +
            InteractiveEventSubSubscriptionPolicy.eventTypesFor(session, channel)

        return eventTypes
            .distinct()
            .mapNotNull { type ->
                if (type in MODERATOR_EVENT_TYPES && channel.id !in moderatedChannelIds) {
                    null
                } else {
                    subscription(
                        broadcasterId = channel.id,
                        type = type,
                    )
                }
            }
    }

    fun subscription(
        broadcasterId: String,
        type: String,
    ): TwitchEventSubSubscriptionSpec {
        val normalizedBroadcasterId = broadcasterId.trim()
        val normalizedType = type.trim()
        require(normalizedBroadcasterId.isNotBlank()) { "EventSub broadcaster id must not be blank" }
        require(normalizedType.isNotBlank()) { "EventSub type must not be blank" }
        return TwitchEventSubSubscriptionSpec(
            broadcasterId = normalizedBroadcasterId,
            type = normalizedType,
            version = versionFor(normalizedType),
            identityConditionKey = identityConditionKeyFor(normalizedType),
        )
    }

    fun versionFor(type: String): String = when (type) {
        "automod.message.hold",
        "automod.message.update",
        "channel.moderate" -> "2"

        else -> "1"
    }

    fun identityConditionKeyFor(type: String): String? = when {
        type in InteractiveEventSubSubscriptionPolicy.ALL_EVENT_TYPES -> null
        type in MODERATOR_EVENT_TYPES -> "moderator_user_id"
        else -> "user_id"
    }
}
