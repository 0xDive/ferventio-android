package io.ferventio.shared.chat

import io.ferventio.app.domain.AutoModBoundary
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatFragment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal sealed interface TwitchAutoModEvent {
    val message: AutoModHeldMessage

    data class Held(
        override val message: AutoModHeldMessage,
    ) : TwitchAutoModEvent

    data class Updated(
        override val message: AutoModHeldMessage,
    ) : TwitchAutoModEvent
}

internal object TwitchAutoModEventParser {
    fun parse(envelope: TwitchEventSubProtocolEnvelope): TwitchAutoModEvent? {
        if (envelope.type != "notification") return null
        val type = envelope.subscriptionType ?: return null
        if (type != HOLD_TYPE && type != UPDATE_TYPE) return null
        val event = envelope.eventPayload
            ?: throw IllegalArgumentException("$type is missing its event payload")
        val status = if (type == HOLD_TYPE) {
            AutoModMessageStatus.HELD
        } else {
            when (event.string("status")?.lowercase()) {
                "approved" -> AutoModMessageStatus.APPROVED
                "denied" -> AutoModMessageStatus.DENIED
                "expired" -> AutoModMessageStatus.EXPIRED
                else -> return null
            }
        }
        val message = parseEvent(
            event = event,
            timestamp = envelope.messageTimestamp.orEmpty(),
            status = status,
        )
        return if (type == HOLD_TYPE) TwitchAutoModEvent.Held(message)
        else TwitchAutoModEvent.Updated(message)
    }

    internal fun parseEvent(
        event: JsonObject,
        timestamp: String,
        status: AutoModMessageStatus,
    ): AutoModHeldMessage {
        val channelId = event.requiredString("broadcaster_user_id")
        val messageId = event.requiredString("message_id")
        val messageObject = event.objectOrNull("message")
        val text = messageObject?.string("text") ?: event.string("message").orEmpty()
        val automod = event.objectOrEmpty("automod")
        val boundaries = buildList {
            event.collectBoundaries(this)
        }.distinct()
        return AutoModHeldMessage(
            channelId = channelId,
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            channelName = event.string("broadcaster_user_name")
                ?: event.string("broadcaster_user_login").orEmpty(),
            userId = event.string("user_id").orEmpty(),
            userLogin = event.string("user_login").orEmpty(),
            userName = event.string("user_name") ?: event.string("user_login").orEmpty(),
            messageId = messageId,
            text = text,
            fragments = listOf(ChatFragment.Text(text)),
            reason = event.string("reason"),
            category = automod.string("category") ?: event.string("category"),
            level = automod.int("level") ?: event.int("level"),
            boundaries = boundaries,
            heldAt = event.string("held_at") ?: timestamp,
            status = status,
            decidedByUserId = event.string("moderator_user_id"),
            decidedByUserLogin = event.string("moderator_user_login"),
            decidedByUserName = event.string("moderator_user_name"),
        )
    }

    private fun JsonElement.collectBoundaries(target: MutableList<AutoModBoundary>) {
        when (this) {
            is JsonObject -> {
                val start = int("start_pos") ?: int("start")
                val end = int("end_pos") ?: int("end")
                if (start != null && end != null && end >= start) {
                    target += AutoModBoundary(start = start, endInclusive = end)
                }
                values.forEach { value -> value.collectBoundaries(target) }
            }
            is JsonArray -> forEach { value -> value.collectBoundaries(target) }
            else -> Unit
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("AutoMod EventSub event is missing $name")

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.objectOrEmpty(name: String): JsonObject =
        objectOrNull(name) ?: EMPTY_OBJECT

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

    private val EMPTY_OBJECT = JsonObject(emptyMap())
    private const val HOLD_TYPE = "automod.message.hold"
    private const val UPDATE_TYPE = "automod.message.update"
}
