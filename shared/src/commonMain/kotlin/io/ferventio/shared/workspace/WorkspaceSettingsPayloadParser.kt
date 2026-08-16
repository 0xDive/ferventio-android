package io.ferventio.shared.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PersistedWorkspaceChannels(
    val logins: List<String>,
    val selectedLogin: String?,
    val pinnedChannelIds: List<String>,
)

/** Minimal forward-compatible projection of Android's synced SettingsBackupDocument. */
object WorkspaceSettingsPayloadParser {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): PersistedWorkspaceChannels {
        require(payload.length <= MAX_PAYLOAD_CHARS) { "Settings payload is too large" }
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid settings payload JSON", it) }
        val content = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val channels = content["channels"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain channels")

        val logins = channels["logins"]
            ?.runCatching {
                jsonArray.mapNotNull { item ->
                    item.jsonPrimitive.contentOrNull
                }
            }
            ?.getOrNull()
            ?: error("Settings payload channels do not contain logins")
        val selectedLogin = channels["selectedLogin"]
            ?.runCatching { jsonPrimitive.contentOrNull }
            ?.getOrNull()
        val pinnedChannelIds = channels["pinnedChannelIds"]
            ?.runCatching {
                jsonArray.mapNotNull { item ->
                    item.jsonPrimitive.contentOrNull
                }
            }
            ?.getOrNull()
            .orEmpty()

        val normalizedLogins = WorkspaceChannelBootstrapPolicy.normalizeLogins(logins)
        val normalizedSelectedLogin = selectedLogin
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it in normalizedLogins }
        return PersistedWorkspaceChannels(
            logins = normalizedLogins,
            selectedLogin = normalizedSelectedLogin,
            pinnedChannelIds = normalizeIds(pinnedChannelIds),
        )
    }

    private fun normalizeIds(values: Iterable<String>): List<String> {
        val seen = hashSetOf<String>()
        return buildList {
            values.forEach { raw ->
                val normalized = raw.trim()
                if (normalized.isNotEmpty() && seen.add(normalized)) {
                    add(normalized)
                }
            }
        }
    }
}
