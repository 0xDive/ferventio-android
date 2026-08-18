package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import kotlin.Throws

data class WorkspaceBootstrapOutcome(
    val remoteSettingsAvailable: Boolean,
    val settingsRevision: Long,
    val channelCount: Int,
)

/** Restores shared workspace/settings state from sync and fresh Twitch channel metadata. */
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
    ): WorkspaceBootstrapOutcome = bootstrap(
        identity = identity,
        authentication = authentication,
        state = state,
        settingsState = null,
    )

    @Throws(Exception::class)
    suspend fun bootstrap(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder?,
    ): WorkspaceBootstrapOutcome {
        val snapshot = settings.fetch(identity, authentication)
            ?: return WorkspaceBootstrapOutcome(
                remoteSettingsAvailable = false,
                settingsRevision = 0L,
                channelCount = state.channels.size,
            )

        settingsState?.restore(snapshot.preferences, snapshot.revision)

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

        val session = authentication.accessLease?.session
            ?: error("Workspace bootstrap requires a Twitch access lease")
        val moderatedChannelIds = runCatching {
            directory.resolveModeratedChannelIds(authentication)
        }.getOrDefault(emptySet()) + session.userId
        state.updateModeratorChannelIds(moderatedChannelIds)

        return WorkspaceBootstrapOutcome(
            remoteSettingsAvailable = true,
            settingsRevision = snapshot.revision,
            channelCount = resolved.channels.size,
        )
    }

    @Throws(Exception::class)
    suspend fun savePreferences(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        preferences: SharedAppPreferences,
        settingsState: SharedAppSettingsStateHolder,
    ): WorkspaceSettingsSnapshot {
        settingsState.markSaveStarted()
        return try {
            settings.updatePreferences(
                identity = identity,
                authentication = authentication,
                preferences = preferences,
            ).also { snapshot ->
                settingsState.markSaveSucceeded(snapshot.preferences, snapshot.revision)
            }
        } catch (error: Throwable) {
            settingsState.markSaveFailed(error.message)
            throw error
        }
    }
}
