package io.ferventio.shared.workspace

import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceLayoutCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

/** Reads the Android-compatible workspace-layout projection from a synced settings payload. */
object SharedWorkspaceLayoutPayloadCodec {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        payload: String,
        fallbackChannelId: String? = null,
    ): WorkspaceLayout {
        require(payload.length <= MAX_PAYLOAD_CHARS) { "Settings payload is too large" }
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid settings payload JSON", it) }
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
}
