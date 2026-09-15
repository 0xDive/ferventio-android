package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import kotlin.time.Instant

internal data class WorkspaceSettingsBackupImportFlowResult(
    val localApply: WorkspaceSettingsBackupLocalApplyOutcome,
    val syncResult: WorkspaceSettingsBackupImportSyncResult,
)

internal data class WorkspaceSettingsBackupImportServerResolution(
    val localApply: WorkspaceSettingsBackupLocalApplyOutcome,
    val revision: Long,
)

/**
 * Owns the visible-state side of a durable settings import transaction.
 *
 * A new import snapshots the current shared projections before applying the imported file. Pending
 * imports can then survive process restart: ordinary retries never force a conflict, an existing
 * durable conflict is surfaced without another PUT, and each conflict side remains an explicit
 * action. No file picker or Compose navigation depends on this coordinator.
 */
internal class WorkspaceSettingsBackupImportFlowCoordinator(
    private val transactions: WorkspaceSettingsBackupImportCoordinator,
    private val localApply: WorkspaceSettingsBackupLocalApplyCoordinator,
) {
    suspend fun start(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        importedPayload: String,
        currentAppVersion: String,
        createdAt: Instant,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupImportFlowResult {
        val started = transactions.begin(
            identity = identity,
            authentication = authentication,
            importedPayload = importedPayload,
            rollbackPayload = { remotePayload ->
                SharedSettingsBackupCodec.captureCurrent(
                    basePayload = remotePayload,
                    preferences = settingsState.preferences,
                    channelLogins = state.channels.map { channel -> channel.login },
                    selectedChannelLogin = state.selectedChannelId?.let { selectedId ->
                        state.channels.firstOrNull { channel -> channel.id == selectedId }?.login
                    },
                    pinnedChannelIds = state.pinnedChannelIds,
                    channelTabTitles = state.channelTabTitles,
                    workspaceLayout = state.workspaceLayout,
                    messageRules = rulesState.snapshot,
                    savedFilters = filtersState.snapshot,
                    currentAppVersion = currentAppVersion,
                    createdAt = createdAt,
                )
            },
        )
        val pending = requireNotNull(started.pendingImport)
        val applied = applyPrepared(
            prepared = pending,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            rulesState = rulesState,
            filtersState = filtersState,
        )
        return syncApplied(
            local = applied,
            identity = identity,
            authentication = authentication,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
            state = state,
            settingsState = settingsState,
        )
    }

    suspend fun resume(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: Instant,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupImportFlowResult {
        val restored = transactions.restore()
        val pending = restored.pendingImport
            ?: error("No pending settings import is available")
        val applied = applyPrepared(
            prepared = pending,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            rulesState = rulesState,
            filtersState = filtersState,
        )
        val conflict = restored.conflict
        if (conflict != null) {
            return WorkspaceSettingsBackupImportFlowResult(
                localApply = applied,
                syncResult = WorkspaceSettingsBackupImportSyncResult.Conflict(
                    serverRevision = conflict.serverRevision,
                    serverImport = conflict.serverImport,
                ),
            )
        }
        return syncApplied(
            local = applied,
            identity = identity,
            authentication = authentication,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
            state = state,
            settingsState = settingsState,
        )
    }

    suspend fun keepLocal(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: Instant,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupImportFlowResult {
        val restored = transactions.restore()
        val pending = restored.pendingImport
            ?: error("No pending settings import is available")
        require(restored.conflict != null) {
            "No settings import conflict is available"
        }
        val applied = applyPrepared(
            prepared = pending,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            rulesState = rulesState,
            filtersState = filtersState,
        )
        val synced = transactions.keepLocal(
            identity = identity,
            authentication = authentication,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
        )
        markSyncedRevision(
            revision = synced.revision,
            state = state,
            settingsState = settingsState,
        )
        return WorkspaceSettingsBackupImportFlowResult(
            localApply = applied,
            syncResult = synced,
        )
    }

    suspend fun useServer(
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupImportServerResolution {
        val conflict = transactions.restore().conflict
            ?: error("No settings import conflict is available")
        val applied = applyPrepared(
            prepared = conflict.serverImport,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            rulesState = rulesState,
            filtersState = filtersState,
        )
        markSyncedRevision(
            revision = conflict.serverRevision,
            state = state,
            settingsState = settingsState,
        )
        transactions.acceptServerConflict(conflict.serverRevision)
        return WorkspaceSettingsBackupImportServerResolution(
            localApply = applied,
            revision = conflict.serverRevision,
        )
    }

    private suspend fun applyPrepared(
        prepared: WorkspaceSettingsPreparedImport,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupLocalApplyOutcome = localApply.apply(
        prepared = prepared,
        authentication = authentication,
        state = state,
        settingsState = settingsState,
        rulesState = rulesState,
        filtersState = filtersState,
    )

    private suspend fun syncApplied(
        local: WorkspaceSettingsBackupLocalApplyOutcome,
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: Instant,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
    ): WorkspaceSettingsBackupImportFlowResult {
        val sync = transactions.syncPending(
            identity = identity,
            authentication = authentication,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
        )
        if (sync is WorkspaceSettingsBackupImportSyncResult.Synced) {
            markSyncedRevision(
                revision = sync.revision,
                state = state,
                settingsState = settingsState,
            )
        }
        return WorkspaceSettingsBackupImportFlowResult(
            localApply = local,
            syncResult = sync,
        )
    }

    private fun markSyncedRevision(
        revision: Long,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
    ) {
        settingsState.restore(settingsState.preferences, revision)
        state.markLoadReady(revision)
    }
}
