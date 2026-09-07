package io.ferventio.shared.settings

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageRuleCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Android-backup-compatible projection for highlight and ignore rules. */
data class SharedMessageRulesSnapshot(
    val highlightRules: List<HighlightRule> = emptyList(),
    val ignoreRules: List<IgnoreRule> = emptyList(),
)

object SharedMessageRulesPayloadCodec {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private const val BACKUP_FORMAT = "ferventio-settings-backup"
    private const val CURRENT_FORMAT_VERSION = 2
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): SharedMessageRulesSnapshot {
        val content = parseRoot(payload)["content"]
            ?.runCatching { jsonObject }
            ?.getOrNull()
            ?: return SharedMessageRulesSnapshot()
        return SharedMessageRulesSnapshot(
            highlightRules = MessageRuleCodec.decodeHighlights(
                content["highlights"]?.runCatching { jsonArray.toString() }?.getOrNull(),
            ),
            ignoreRules = MessageRuleCodec.decodeIgnores(
                content["ignoreRules"]?.runCatching { jsonArray.toString() }?.getOrNull(),
            ),
        )
    }

    /**
     * Replaces only highlight/ignore projections while preserving every other Android backup
     * section and recomputing the same SHA-256 contentHash used by SettingsBackupCodec.
     */
    fun replace(
        payload: String,
        rules: SharedMessageRulesSnapshot,
    ): String {
        val root = parseRoot(payload)
        require(root.string("format") == BACKUP_FORMAT) {
            "Unsupported Ferventio settings payload format"
        }
        val original = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val highlights = json.parseToJsonElement(
            MessageRuleCodec.encodeHighlights(rules.highlightRules),
        ).jsonArray
        val ignores = json.parseToJsonElement(
            MessageRuleCodec.encodeIgnores(rules.ignoreRules),
        ).jsonArray
        val content = buildJsonObject {
            put("settings", original["settings"] ?: JsonObject(emptyMap()))
            put("channels", original["channels"] ?: JsonObject(emptyMap()))
            put("workspaces", original["workspaces"] ?: JsonNull)
            put("filters", original["filters"] ?: JsonObject(emptyMap()))
            put("highlights", highlights)
            put("ignoreRules", ignores)
            put("commands", original["commands"] ?: JsonObject(emptyMap()))
            put("favouriteEmotes", original["favouriteEmotes"] ?: JsonArray(emptyList()))
        }
        val createdAt = root.string("createdAt")
            ?: error("Settings payload does not contain createdAt")
        val appVersion = root.string("appVersion")
            ?: error("Settings payload does not contain appVersion")
        val contentHash = sha256Hex(content.toString())
        return buildJsonObject {
            put("format", JsonPrimitive(BACKUP_FORMAT))
            put("formatVersion", JsonPrimitive(CURRENT_FORMAT_VERSION))
            put("createdAt", JsonPrimitive(createdAt))
            put("appVersion", JsonPrimitive(appVersion))
            put("contentHash", JsonPrimitive(contentHash))
            put("content", content)
            root.forEach { (name, value) ->
                if (name !in DOCUMENT_FIELDS) put(name, value)
            }
        }.toString()
    }

    internal fun contentHashForTesting(payload: String): String {
        val root = parseRoot(payload)
        val content = root["content"]?.jsonObject
            ?: error("Settings payload does not contain content")
        return sha256Hex(content.toString())
    }

    private fun parseRoot(payload: String): JsonObject {
        require(payload.length <= MAX_PAYLOAD_CHARS) { "Settings payload is too large" }
        return runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid settings payload JSON", it) }
    }

    private fun JsonObject.string(name: String): String? = this[name]
        ?.runCatching { jsonPrimitive.contentOrNull }
        ?.getOrNull()

    private val DOCUMENT_FIELDS = setOf(
        "format",
        "formatVersion",
        "createdAt",
        "appVersion",
        "contentHash",
        "content",
    )
}
