package io.ferventio.shared.chat

import io.ferventio.app.domain.security.JsonInputGuard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class TwitchEventSubProtocolEnvelope(
    val type: String,
    val messageId: String? = null,
    val messageTimestamp: String? = null,
    val sessionId: String? = null,
    val reconnectUrl: String? = null,
    val keepaliveTimeoutSeconds: Int? = null,
    val subscriptionType: String? = null,
    val revocationStatus: String? = null,
    val eventPayload: JsonObject? = null,
)

/**
 * Parses the transport-level EventSub envelope without interpreting notification payloads.
 * Rich chat/moderation event mapping remains a separate layer so socket lifecycle can migrate first.
 */
object TwitchEventSubProtocolParser {
    const val MAX_ENVELOPE_CHARS = 256 * 1024
    const val MAX_JSON_DEPTH = 64

    private val json = Json { ignoreUnknownKeys = true }
    private val emptyObject = JsonObject(emptyMap())

    fun parse(raw: String): TwitchEventSubProtocolEnvelope {
        JsonInputGuard.requireWithinLimits(
            raw = raw,
            maxChars = MAX_ENVELOPE_CHARS,
            maxNestingDepth = MAX_JSON_DEPTH,
            inputName = "EventSub envelope",
        )
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("EventSub root must be a JSON object")
        val metadata = root.objectOrEmpty("metadata")
        val payload = root.objectOrEmpty("payload")
        val type = metadata.string("message_type").orEmpty()
        val messageId = metadata.string("message_id")
        val timestamp = metadata.string("message_timestamp")

        return when (type) {
            "session_welcome",
            "session_reconnect" -> {
                val session = payload.objectOrEmpty("session")
                TwitchEventSubProtocolEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    sessionId = session.string("id"),
                    reconnectUrl = session.string("reconnect_url"),
                    keepaliveTimeoutSeconds = session.int("keepalive_timeout_seconds"),
                )
            }

            "notification" -> {
                val subscription = payload.objectOrEmpty("subscription")
                TwitchEventSubProtocolEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    subscriptionType = subscription.string("type"),
                    eventPayload = payload["event"] as? JsonObject,
                )
            }

            "revocation" -> {
                val subscription = payload.objectOrEmpty("subscription")
                TwitchEventSubProtocolEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    subscriptionType = subscription.string("type"),
                    revocationStatus = subscription.string("status"),
                )
            }

            else -> TwitchEventSubProtocolEnvelope(
                type = type,
                messageId = messageId,
                messageTimestamp = timestamp,
            )
        }
    }

    private fun JsonObject.objectOrEmpty(key: String): JsonObject =
        this[key] as? JsonObject ?: emptyObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
}
