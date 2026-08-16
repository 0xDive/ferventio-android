package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import kotlin.Throws

data class WorkspaceBootstrapOutcome(
    val remoteSettingsAvailable: Boolean,
    val settingsRevision: Long,
    val channelCount: Int,
)

/** Restores the shared workspace from synced settings and fresh Twitch channel metadata. */
class WorkspaceBootstrapCoordinator(
    private val settings: WorkspaceSettingsSyncClient = WorkspaceSettingsSyncClient(),
    private val directory: TwitchChannelDirectoryClient = TwitchChannelDirectoryClient(),
) {
    constructor() : this(
        settings = WorkspaceSettingsSyncClient(),
        directory = TwitchChannelDirectoryClient(),
    )

    @Throws(Exception::class)
    suspend fun bootstrap(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
    ): WorkspaceBootstrapOutcome {
        val snapshot = settings.fetch(identity, authentication)
            ?: return WorkspaceBootstrapOutcome(
                remoteSettingsAvailable = false,
                settingsRevision = 0L,
                channelCount = state.channels.size,
            )

        val refreshed = directory.resolveByLogins(
            authentication = authentication,
            logins = snapshot.channels.logins,
        )
        val resolved = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = snapshot.channels.logins,
            cachedChannels = state.channels,
            refreshedChannels = refreshed,
            selectedLogin = snapshot.channels.selectedLogin,
        )

        state.replaceChannels(resolved.channels)
        resolved.selectedChannelId?.let(state::selectChannel)
        state.updatePinnedChannelIds(snapshot.channels.pinnedChannelIds)

        return WorkspaceBootstrapOutcome(
            remoteSettingsAvailable = true,
            settingsRevision = snapshot.revision,
            channelCount = resolved.channels.size,
        )
    }
}
