package io.ferventio.shared.settings

import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.SavedMessageFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Android-backup-compatible projection for saved message filters. */
data class SharedSavedFiltersSnapshot(
    val filters: List<SavedMessageFilter> = emptyList(),
)

object SharedSavedFiltersPayloadCodec {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private const val BACKUP_FORMAT = "ferventio-settings-backup"
    private const val CURRENT_FORMAT_VERSION = 2
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): SharedSavedFiltersSnapshot {
        val content = parseRoot(payload)["content"]
            ?.runCatching { jsonObject }
            ?.getOrNull()
            ?: return SharedSavedFiltersSnapshot()
        val rawFilters = content["filters"]?.toString()
        val filters = MessageFilterCodec.decode(rawFilters)
            .getOrElse { error ->
                throw IllegalArgumentException("Invalid saved message filters", error)
            }
        return SharedSavedFiltersSnapshot(filters = filters)
    }

    /**
     * Replaces only the saved-filter projection while preserving every other Android backup
     * section and recomputing the same SHA-256 contentHash used by SettingsBackupCodec.
     */
    fun replace(
        payload: String,
        snapshot: SharedSavedFiltersSnapshot,
    ): String {
        val root = parseRoot(payload)
        require(root.string("format") == BACKUP_FORMAT) {
            "Unsupported Ferventio settings payload format"
        }
        val original = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val filters = json.parseToJsonElement(
            MessageFilterCodec.encode(snapshot.filters),
        ).jsonObject
        val content = buildJsonObject {
            put("settings", original["settings"] ?: JsonObject(emptyMap()))
            put("channels", original["channels"] ?: JsonObject(emptyMap()))
            put("workspaces", original["workspaces"] ?: JsonNull)
            put("filters", filters)
            put("highlights", original["highlights"] ?: JsonArray(emptyList()))
            put("ignoreRules", original["ignoreRules"] ?: JsonArray(emptyList()))
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
