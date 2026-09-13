package io.ferventio.shared.workspace

import android.content.Context
import io.ferventio.shared.settings.FERVENTIO_ANDROID_SETTINGS_FILE_NAME

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
        if (snapshot.selectedChannelLogin == null) {
            editor.remove(ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
        } else {
            editor.putString(
                ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY,
                snapshot.selectedChannelLogin,
            )
        }
        check(editor.commit()) { "Failed to persist anonymous workspace" }
    }
}

fun androidAnonymousWorkspaceCoordinator(
    context: Context,
): AnonymousWorkspaceCoordinator = AnonymousWorkspaceCoordinator(
    store = AndroidAnonymousWorkspaceStore(context),
)
