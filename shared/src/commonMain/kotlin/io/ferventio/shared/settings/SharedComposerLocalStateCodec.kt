package io.ferventio.shared.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Device-local composer persistence shared by Android and iOS.
 *
 * JSON layout and keys intentionally match the legacy Android SettingsStore values.
 */
internal object SharedComposerLocalStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decodeDrafts(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.mapNotNull { (channelId, value) ->
                val normalizedChannelId = channelId.trim()
                val draft = value.jsonPrimitive.contentOrNull
                    ?.take(MAX_COMPOSER_DRAFT_LENGTH)
                    .orEmpty()
                if (normalizedChannelId.isEmpty() || draft.isEmpty()) {
                    null
                } else {
                    normalizedChannelId to draft
                }
            }.take(MAX_LOCAL_CHANNELS).toMap()
        }.getOrDefault(emptyMap())
    }

    fun encodeDrafts(values: Map<String, String>): String = JsonObject(
        values.entries
            .asSequence()
            .mapNotNull { (rawChannelId, rawDraft) ->
                val channelId = rawChannelId.trim()
                val draft = rawDraft.take(MAX_COMPOSER_DRAFT_LENGTH)
                if (channelId.isEmpty() || draft.isEmpty()) null
                else channelId to JsonPrimitive(draft)
            }
            .take(MAX_LOCAL_CHANNELS)
            .toMap(),
    ).toString()

    fun decodeHistory(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.mapNotNull { (channelId, value) ->
                val normalizedChannelId = channelId.trim()
                if (normalizedChannelId.isEmpty()) return@mapNotNull null
                val messages = value.jsonArray
                    .mapNotNull { item -> item.jsonPrimitive.contentOrNull }
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { it.take(MAX_COMPOSER_DRAFT_LENGTH) }
                    .distinct()
                    .take(MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL)
                normalizedChannelId
                    .takeIf { messages.isNotEmpty() }
                    ?.let { it to messages }
            }.take(MAX_LOCAL_CHANNELS).toMap()
        }.getOrDefault(emptyMap())
    }

    fun encodeHistory(values: Map<String, List<String>>): String = JsonObject(
        values.entries
            .asSequence()
            .mapNotNull { (rawChannelId, rawMessages) ->
                val channelId = rawChannelId.trim()
                if (channelId.isEmpty()) return@mapNotNull null
                val messages = rawMessages.asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { it.take(MAX_COMPOSER_DRAFT_LENGTH) }
                    .distinct()
                    .take(MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL)
                    .map(::JsonPrimitive)
                    .toList()
                if (messages.isEmpty()) null
                else channelId to JsonArray(messages)
            }
            .take(MAX_LOCAL_CHANNELS)
            .toMap(),
    ).toString()

    private const val MAX_LOCAL_CHANNELS = 100
}
