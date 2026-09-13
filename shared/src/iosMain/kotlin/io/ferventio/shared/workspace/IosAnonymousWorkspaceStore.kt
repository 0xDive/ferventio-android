package io.ferventio.shared.workspace

import platform.Foundation.NSUserDefaults

/** iOS device-local anonymous workspace persistence mirroring Android's channel keys. */
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
        snapshot.selectedChannelLogin?.let { login ->
            defaults.setObject(login, forKey = ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
        } ?: defaults.removeObjectForKey(ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY)
    }
}

fun iosAnonymousWorkspaceCoordinator(): AnonymousWorkspaceCoordinator =
    AnonymousWorkspaceCoordinator(IosAnonymousWorkspaceStore())
