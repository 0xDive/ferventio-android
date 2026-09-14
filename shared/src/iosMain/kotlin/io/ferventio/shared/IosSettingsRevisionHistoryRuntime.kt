package io.ferventio.shared

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.ui.app.SharedSettingsRevisionHistoryStateHolder
import io.ferventio.shared.workspace.WorkspaceSettingsBackupImportPreparation
import io.ferventio.shared.workspace.WorkspaceSettingsBackupLocalApplyCoordinator
import io.ferventio.shared.workspace.WorkspaceSettingsHistoryClient
import io.ferventio.shared.workspace.WorkspaceSettingsHistoryEntry
import kotlin.Throws

/** iOS-facing facade for settings revision history and restore operations. */
class IosSettingsRevisionHistoryRuntime(
    private val runtime: FerventioRuntimeState,
) {
    val state = SharedSettingsRevisionHistoryStateHolder()

    private val history = WorkspaceSettingsHistoryClient()
    private val localApply = WorkspaceSettingsBackupLocalApplyCoordinator()

    @Throws(Exception::class)
    suspend fun loadHistory(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): List<WorkspaceSettingsHistoryEntry> {
        state.markLoading()
        return try {
            history.fetchHistory(identity, authentication).also(state::markLoaded)
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun restoreRevision(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        revision: Long,
    ): Long {
        state.markRestoring(revision)
        return try {
            val snapshot = history.restoreRevision(
                identity = identity,
                authentication = authentication,
                revision = revision,
            )
            val prepared = WorkspaceSettingsBackupImportPreparation.prepare(snapshot.payload)
            localApply.apply(
                prepared = prepared,
                authentication = authentication,
                state = runtime.workspace,
                settingsState = runtime.settings,
                rulesState = runtime.messageRules,
                filtersState = runtime.savedFilters,
            )
            runtime.settings.restore(runtime.settings.preferences, snapshot.revision)
            runtime.workspace.markLoadReady(snapshot.revision)
            state.markRestored(snapshot.revision)
            snapshot.revision
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }
}
