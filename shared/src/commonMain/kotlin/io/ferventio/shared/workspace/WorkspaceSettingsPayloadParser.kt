package io.ferventio.shared.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PersistedWorkspaceChannels(
    val logins: List<String>,
    val selectedLogin: String?,
    val favouriteChannelIds: List<String> = emptyList(),
    val pinnedChannelIds: List<String> = emptyList(),
    val recentChannelIds: List<String> = emptyList(),
    val tabTitles: Map<String, String> = emptyMap(),
)

/** Forward-compatible projection of Android's synced SettingsBackupDocument channel state. */
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
                jsonArray.mapNotNull { item -> item.jsonPrimitive.contentOrNull }
            }
            ?.getOrNull()
            ?: error("Settings payload channels do not contain logins")
        val normalizedLogins = WorkspaceChannelBootstrapPolicy.normalizeLogins(logins)
        val selectedLogin = channels["selectedLogin"]
            ?.runCatching { jsonPrimitive.contentOrNull }
            ?.getOrNull()
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it in normalizedLogins }

        val tabTitles = channels["tabTitles"]
            ?.runCatching { jsonObject }
            ?.getOrNull()
            ?.mapNotNull { (channelId, value) ->
                val normalizedId = channelId.trim()
                val title = value.runCatching { jsonPrimitive.contentOrNull }
                    .getOrNull()
                    ?.trim()
                    ?.take(32)
                    .orEmpty()
                if (normalizedId.isEmpty() || title.isEmpty()) null else normalizedId to title
            }
            ?.toMap(linkedMapOf())
            .orEmpty()

        return PersistedWorkspaceChannels(
            logins = normalizedLogins,
            selectedLogin = selectedLogin,
            favouriteChannelIds = normalizeIds(readStringArray(channels, "favouriteChannelIds")),
            pinnedChannelIds = normalizeIds(readStringArray(channels, "pinnedChannelIds")),
            recentChannelIds = normalizeIds(readStringArray(channels, "recentChannelIds")),
            tabTitles = tabTitles,
        )
    }

    private fun readStringArray(
        channels: kotlinx.serialization.json.JsonObject,
        name: String,
    ): List<String> = channels[name]
        ?.runCatching {
            jsonArray.mapNotNull { item -> item.jsonPrimitive.contentOrNull }
        }
        ?.getOrNull()
        .orEmpty()

    private fun normalizeIds(values: Iterable<String>): List<String> {
        val seen = hashSetOf<String>()
        return buildList {
            values.forEach { raw ->
                val normalized = raw.trim()
                if (normalized.isNotEmpty() && seen.add(normalized)) add(normalized)
            }
        }
    }
}
