package io.ferventio.app.twitch

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun buildBanOrTimeoutPayload(
    targetUserId: String,
    durationSeconds: Int?,
    reason: String,
): JsonObject = buildJsonObject {
    put("data", buildJsonObject {
        put("user_id", JsonPrimitive(targetUserId))
        durationSeconds?.let { duration ->
            put("duration", JsonPrimitive(duration))
            reason.trim()
                .take(500)
                .takeIf(String::isNotBlank)
                ?.let { cleanReason -> put("reason", JsonPrimitive(cleanReason)) }
        }
    })
}
