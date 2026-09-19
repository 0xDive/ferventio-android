package io.ferventio.shared.workspace

import android.content.Context
import io.ferventio.shared.settings.FERVENTIO_ANDROID_SETTINGS_FILE_NAME
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Android anonymous workspace persistence compatible with the legacy production preferences. */
class AndroidAnonymousWorkspaceStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : AnonymousWorkspaceStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): AnonymousWorkspaceSnapshot = AnonymousWorkspaceSnapshot(
        channelLogins = preferences.getString(ANONYMOUS_WORKSPACE_CHANNELS_KEY, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty),
        selectedChannelLogin = preferences.getString(
            ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY,
            null,
        ),
        pinnedChannelLogins = preferences.getString(ANONYMOUS_WORKSPACE_PINNED_LOGINS_KEY, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty),
        channelTitlesByLogin = decodeTitles(
            preferences.getString(ANONYMOUS_WORKSPACE_TITLES_KEY, null),
        ),
        workspaceLayoutJson = preferences.getString(ANONYMOUS_WORKSPACE_LAYOUT_KEY, null),
    )

    override fun save(snapshot: AnonymousWorkspaceSnapshot) {
        val editor = preferences.edit()
            .putString(
                ANONYMOUS_WORKSPACE_CHANNELS_KEY,
                snapshot.channelLogins.joinToString("|"),
            )
            .putBoolean(
                ANONYMOUS_WORKSPACE_EXPLICITLY_EMPTY_KEY,
                snapshot.channelLogins.isEmpty(),
            )
            .putString(
                ANONYMOUS_WORKSPACE_PINNED_LOGINS_KEY,
                snapshot.pinnedChannelLogins.joinToString("|"),
            )
            .putString(
                ANONYMOUS_WORKSPACE_TITLES_KEY,
                encodeTitles(snapshot.channelTitlesByLogin),
            )
        if (snapshot.selectedChannelLogin == null) {
            editor.remove(ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
        } else {
            editor.putString(
                ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY,
                snapshot.selectedChannelLogin,
            )
        }
        if (snapshot.workspaceLayoutJson == null) {
            editor.remove(ANONYMOUS_WORKSPACE_LAYOUT_KEY)
        } else {
            editor.putString(ANONYMOUS_WORKSPACE_LAYOUT_KEY, snapshot.workspaceLayoutJson)
        }
        check(editor.commit()) { "Failed to persist anonymous workspace" }
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

fun androidAnonymousWorkspaceCoordinator(
    context: Context,
): AnonymousWorkspaceCoordinator = AnonymousWorkspaceCoordinator(
    store = AndroidAnonymousWorkspaceStore(context),
)
