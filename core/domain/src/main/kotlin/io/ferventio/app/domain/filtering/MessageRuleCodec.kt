package io.ferventio.app.domain

import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object MessageRuleCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeHighlights(rules: List<HighlightRule>): String = JsonArray(
        rules.map { rule ->
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(rule.id),
                    "type" to JsonPrimitive(rule.type.name),
                    "pattern" to JsonPrimitive(rule.pattern),
                    "enabled" to JsonPrimitive(rule.enabled),
                    "caseSensitive" to JsonPrimitive(rule.caseSensitive),
                    "colorArgb" to JsonPrimitive(rule.colorArgb),
                    "playSound" to JsonPrimitive(rule.playSound),
                    "push" to JsonPrimitive(rule.push),
                    "addToMentions" to JsonPrimitive(rule.addToMentions),
                    "filteredSplit" to JsonPrimitive(rule.filteredSplit),
                ),
            )
        },
    ).toString()

    fun decodeHighlights(raw: String?): List<HighlightRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val objectValue = element.jsonObject
                val type = objectValue.string("type")
                    ?.let { runCatching { HighlightRuleType.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                HighlightRule(
                    id = objectValue.string("id").orEmpty().ifBlank { Uuid.random().toString() },
                    type = type,
                    pattern = objectValue.string("pattern").orEmpty(),
                    enabled = objectValue.boolean("enabled") ?: true,
                    caseSensitive = objectValue.boolean("caseSensitive") ?: false,
                    colorArgb = objectValue.long("colorArgb") ?: DEFAULT_HIGHLIGHT_COLOR_ARGB,
                    playSound = objectValue.boolean("playSound") ?: false,
                    push = objectValue.boolean("push") ?: false,
                    addToMentions = objectValue.boolean("addToMentions") ?: true,
                    filteredSplit = objectValue.boolean("filteredSplit") ?: false,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeIgnores(rules: List<IgnoreRule>): String = JsonArray(
        rules.map { rule ->
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(rule.id),
                    "type" to JsonPrimitive(rule.type.name),
                    "pattern" to JsonPrimitive(rule.pattern),
                    "enabled" to JsonPrimitive(rule.enabled),
                    "caseSensitive" to JsonPrimitive(rule.caseSensitive),
                    "displayMode" to JsonPrimitive(rule.displayMode.name),
                ),
            )
        },
    ).toString()

    fun decodeIgnores(raw: String?): List<IgnoreRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val objectValue = element.jsonObject
                val type = objectValue.string("type")
                    ?.let { runCatching { IgnoreRuleType.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                val displayMode = objectValue.string("displayMode")
                    ?.let { runCatching { IgnoreDisplayMode.valueOf(it) }.getOrNull() }
                    ?: IgnoreDisplayMode.HIDE
                IgnoreRule(
                    id = objectValue.string("id").orEmpty().ifBlank { Uuid.random().toString() },
                    type = type,
                    pattern = objectValue.string("pattern").orEmpty(),
                    enabled = objectValue.boolean("enabled") ?: true,
                    caseSensitive = objectValue.boolean("caseSensitive") ?: false,
                    displayMode = displayMode,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeReasons(reasons: List<String>): String = JsonArray(reasons.map(::JsonPrimitive)).toString()

    fun decodeReasons(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.boolean(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull
}
