package io.ferventio.shared.settings

import io.ferventio.app.domain.ChannelNotificationPreferences
import io.ferventio.app.domain.NotificationPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object NotificationPreferencesCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): NotificationPreferences {
        if (raw.isNullOrBlank()) return NotificationPreferences()
        return runCatching {
            decodeElement(json.parseToJsonElement(raw))
        }.getOrDefault(NotificationPreferences())
    }

    fun encode(value: NotificationPreferences): String =
        encodeElement(value).toString()

    fun decodeElement(element: JsonElement?): NotificationPreferences {
        val root = element as? JsonObject ?: return NotificationPreferences()
        val eventOverrides = root.booleanMap("eventOverrides")
        val channelOverrides = (root["channelOverrides"] as? JsonObject)
            ?.mapNotNull { (channelId, rawValue) ->
                val channel = rawValue as? JsonObject ?: return@mapNotNull null
                channelId to ChannelNotificationPreferences(
                    enabled = channel.boolean("enabled") ?: true,
                    eventOverrides = channel.booleanMap("eventOverrides"),
                )
            }
            ?.toMap()
            .orEmpty()
        return NotificationPreferences(
            enabled = root.boolean("enabled") ?: true,
            eventOverrides = eventOverrides,
            channelOverrides = channelOverrides,
        ).normalized()
    }

    fun encodeElement(value: NotificationPreferences): JsonObject {
        val normalized = value.normalized()
        return buildJsonObject {
            put("enabled", JsonPrimitive(normalized.enabled))
            put(
                "eventOverrides",
                JsonObject(
                    normalized.eventOverrides.mapValues { (_, enabled) ->
                        JsonPrimitive(enabled)
                    },
                ),
            )
            put(
                "channelOverrides",
                buildJsonObject {
                    normalized.channelOverrides.forEach { (channelId, channel) ->
                        put(
                            channelId,
                            buildJsonObject {
                                put("enabled", JsonPrimitive(channel.enabled))
                                put(
                                    "eventOverrides",
                                    JsonObject(
                                        channel.eventOverrides.mapValues { (_, enabled) ->
                                            JsonPrimitive(enabled)
                                        },
                                    ),
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JsonObject.boolean(name: String): Boolean? =
        get(name)?.runCatching { jsonPrimitive.booleanOrNull }?.getOrNull()

    private fun JsonObject.booleanMap(name: String): Map<String, Boolean> =
        (get(name) as? JsonObject)
            ?.mapNotNull { (key, value) ->
                value.runCatching { jsonPrimitive.booleanOrNull }
                    .getOrNull()
                    ?.let { key to it }
            }
            ?.toMap()
            .orEmpty()
}
