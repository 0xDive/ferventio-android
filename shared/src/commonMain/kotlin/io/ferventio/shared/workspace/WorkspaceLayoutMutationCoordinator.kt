package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.WorkspaceLayout
import kotlin.Throws

/** Persists workspace split mutations without re-running channel-directory bootstrap. */
class WorkspaceLayoutMutationCoordinator(
    private val settings: WorkspaceSettingsSyncClient = WorkspaceSettingsSyncClient(),
) {
    constructor() : this(WorkspaceSettingsSyncClient())

    @Throws(Exception::class)
    suspend fun setSplitFilterQuery(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
        filterQuery: String,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        updateWorkspaceSplitFilterQuery(remote, splitId, filterQuery)
    }

    @Throws(Exception::class)
    suspend fun bindSplitSavedFilter(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
        filterId: String,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        bindWorkspaceSplitSavedFilter(remote, splitId, filterId)
    }

    @Throws(Exception::class)
    suspend fun setSplitHighlightsOnly(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        setWorkspaceSplitHighlightsOnly(remote, splitId)
    }

    @Throws(Exception::class)
    suspend fun clearSplitFilter(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
    ): WorkspaceSettingsSnapshot = setSplitFilterQuery(
        identity = identity,
        authentication = authentication,
        state = state,
        splitId = splitId,
        filterQuery = "",
    )

    @Throws(Exception::class)
    suspend fun setSplitChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
        channelId: String,
    ): WorkspaceSettingsSnapshot {
        require(channelId.trim() in state.channelIds) { "Channel is not in the workspace" }
        return mutateLayout(identity, authentication, state) { remote ->
            updateWorkspaceSplitChannel(remote, splitId, channelId)
        }
    }

    @Throws(Exception::class)
    suspend fun focusSplit(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        focusWorkspaceSplit(remote, splitId)
    }

    @Throws(Exception::class)
    suspend fun addSplit(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        channelId: String? = state.selectedChannelId,
    ): WorkspaceSettingsSnapshot {
        val normalizedChannelId = channelId?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedChannelId == null || normalizedChannelId in state.channelIds) {
            "Channel is not in the workspace"
        }
        return mutateLayout(identity, authentication, state) { remote ->
            addWorkspaceChatSplit(remote, normalizedChannelId)
        }
    }

    @Throws(Exception::class)
    suspend fun removeSplit(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        splitId: String,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        removeWorkspaceSplit(remote, splitId)
    }

    @Throws(Exception::class)
    suspend fun setPrimaryFraction(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        fraction: Float,
    ): WorkspaceSettingsSnapshot = mutateLayout(identity, authentication, state) { remote ->
        updateWorkspaceSplitPrimaryFraction(remote, fraction)
    }

    private suspend fun mutateLayout(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        mutate: (WorkspaceLayout) -> WorkspaceLayout,
    ): WorkspaceSettingsSnapshot {
        state.markMutationStarted()
        return try {
            settings.updateWorkspaceLayout(
                identity = identity,
                authentication = authentication,
                fallbackChannelId = state.selectedChannelId,
                mutate = mutate,
            ).also { snapshot ->
                state.restoreWorkspaceLayout(
                    SharedWorkspaceLayoutPayloadCodec.parse(
                        payload = snapshot.payload,
                        fallbackChannelId = state.selectedChannelId,
                    ),
                )
                state.markLoadReady(snapshot.revision)
                state.markMutationSucceeded()
            }
        } catch (error: Throwable) {
            state.markMutationFailed(error.message)
            throw error
        }
    }
}
