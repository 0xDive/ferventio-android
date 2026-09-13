package io.ferventio.shared.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSUserDefaults

/** iOS device-local anonymous workspace persistence mirroring Android's stable login contract. */
class IosAnonymousWorkspaceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnonymousWorkspaceStore {
    override fun load(): AnonymousWorkspaceSnapshot = AnonymousWorkspaceSnapshot(
        channelLogins = defaults.stringForKey(ANONYMOUS_WORKSPACE_CHANNELS_KEY)
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty),
        selectedChannelLogin = defaults.stringForKey(
            ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY,
        ),
        pinnedChannelLogins = defaults.stringForKey(ANONYMOUS_WORKSPACE_PINNED_LOGINS_KEY)
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty),
        channelTitlesByLogin = decodeTitles(
            defaults.stringForKey(ANONYMOUS_WORKSPACE_TITLES_KEY),
        ),
    )

    override fun save(snapshot: AnonymousWorkspaceSnapshot) {
        defaults.setObject(
            snapshot.channelLogins.joinToString("|"),
            forKey = ANONYMOUS_WORKSPACE_CHANNELS_KEY,
        )
        defaults.setBool(
            snapshot.channelLogins.isEmpty(),
            forKey = ANONYMOUS_WORKSPACE_EXPLICITLY_EMPTY_KEY,
        )
        defaults.setObject(
            snapshot.pinnedChannelLogins.joinToString("|"),
            forKey = ANONYMOUS_WORKSPACE_PINNED_LOGINS_KEY,
        )
        defaults.setObject(
            encodeTitles(snapshot.channelTitlesByLogin),
            forKey = ANONYMOUS_WORKSPACE_TITLES_KEY,
        )
        snapshot.selectedChannelLogin?.let { login ->
            defaults.setObject(login, forKey = ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
        } ?: defaults.removeObjectForKey(ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
    }

    private fun encodeTitles(value: Map<String, String>): String = JsonObject(
        value.mapValues { (_, title) -> JsonPrimitive(title) },
    ).toString()

    private fun decodeTitles(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapNotNull { (login, element) ->
                element.jsonPrimitive.contentOrNull?.let { title -> login to title }
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

fun iosAnonymousWorkspaceCoordinator(): AnonymousWorkspaceCoordinator =
    AnonymousWorkspaceCoordinator(IosAnonymousWorkspaceStore())
