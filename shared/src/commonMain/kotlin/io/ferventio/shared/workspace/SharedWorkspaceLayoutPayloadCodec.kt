package io.ferventio.shared.workspace

import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceLayoutCodec
import io.ferventio.shared.settings.sha256Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads and writes the Android-compatible workspace-layout projection in synced settings. */
object SharedWorkspaceLayoutPayloadCodec {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private const val BACKUP_FORMAT = "ferventio-settings-backup"
    private const val CURRENT_FORMAT_VERSION = 2
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        payload: String,
        fallbackChannelId: String? = null,
    ): WorkspaceLayout {
        val root = parseRoot(payload)
        val content = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val workspaces = content["workspaces"]
            ?.takeUnless { it is JsonNull }
            ?.toString()
        return WorkspaceLayoutCodec.decodeOrDefault(
            raw = workspaces,
            fallbackChannelId = fallbackChannelId,
        )
    }

    /** Replaces only content.workspaces and recomputes the Android backup content hash. */
    fun replace(
        payload: String,
        layout: WorkspaceLayout,
    ): String {
        val root = parseRoot(payload)
        require(root.string("format") == BACKUP_FORMAT) {
            "Unsupported Ferventio settings payload format"
        }
        val original = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val workspaces = json.parseToJsonElement(
            WorkspaceLayoutCodec.encode(layout),
        ).jsonObject
        val content = buildJsonObject {
            put("settings", original["settings"] ?: JsonObject(emptyMap()))
            put("channels", original["channels"] ?: JsonObject(emptyMap()))
            put("workspaces", workspaces)
            put("filters", original["filters"] ?: JsonObject(emptyMap()))
            put("highlights", original["highlights"] ?: JsonArray(emptyList()))
            put("ignoreRules", original["ignoreRules"] ?: JsonArray(emptyList()))
            put("commands", original["commands"] ?: JsonObject(emptyMap()))
            put("favouriteEmotes", original["favouriteEmotes"] ?: JsonArray(emptyList()))
        }
        val createdAt = root.string("createdAt")
            ?: error("Settings payload does not contain createdAt")
        val appVersion = root.string("appVersion")
            ?: error("Settings payload does not contain appVersion")
        return buildJsonObject {
            put("format", JsonPrimitive(BACKUP_FORMAT))
            put("formatVersion", JsonPrimitive(CURRENT_FORMAT_VERSION))
            put("createdAt", JsonPrimitive(createdAt))
            put("appVersion", JsonPrimitive(appVersion))
            put("contentHash", JsonPrimitive(sha256Hex(content.toString())))
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
