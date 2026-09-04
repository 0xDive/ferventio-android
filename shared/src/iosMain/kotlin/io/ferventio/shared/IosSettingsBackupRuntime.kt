package io.ferventio.shared

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ferventio.shared.ui.app.SharedSettingsBackupStateHolder
import io.ferventio.shared.workspace.WorkspaceSettingsBackupImportCoordinator
import io.ferventio.shared.workspace.WorkspaceSettingsBackupImportFlowCoordinator
import io.ferventio.shared.workspace.WorkspaceSettingsBackupImportSyncResult
import io.ferventio.shared.workspace.WorkspaceSettingsBackupLocalApplyCoordinator
import io.ferventio.shared.workspace.WorkspaceSettingsBackupUploadClient
import io.ferventio.shared.workspace.WorkspaceSettingsSyncClient
import io.ferventio.shared.workspace.createIosWorkspaceSettingsBackupImportJournal
import kotlin.Throws
import kotlin.time.Instant

/** iOS-facing facade for the shared Android-compatible settings backup transaction. */
class IosSettingsBackupRuntime internal constructor(
    private val runtime: FerventioRuntimeState,
) {
    val state = SharedSettingsBackupStateHolder()

    private val snapshots = WorkspaceSettingsSyncClient()
    private val journal = createIosWorkspaceSettingsBackupImportJournal()
    private val transactions = WorkspaceSettingsBackupImportCoordinator(
        snapshots = snapshots,
        uploads = WorkspaceSettingsBackupUploadClient(),
        journal = journal,
    )
    private val flow = WorkspaceSettingsBackupImportFlowCoordinator(
        transactions = transactions,
        localApply = WorkspaceSettingsBackupLocalApplyCoordinator(),
    )

    @Throws(Exception::class)
    suspend fun exportBackup(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: String,
    ): String {
        state.markExporting()
        return try {
            val remote = snapshots.fetch(identity, authentication)
            SharedSettingsBackupCodec.captureCurrent(
                basePayload = remote?.payload,
                preferences = runtime.settings.preferences,
                channelLogins = runtime.workspace.channels.map { channel -> channel.login },
                selectedChannelLogin = runtime.workspace.selectedChannelId?.let { selectedId ->
                    runtime.workspace.channels.firstOrNull { channel -> channel.id == selectedId }?.login
                },
                pinnedChannelIds = runtime.workspace.pinnedChannelIds,
                channelTabTitles = runtime.workspace.channelTabTitles,
                workspaceLayout = runtime.workspace.workspaceLayout,
                messageRules = runtime.messageRules.snapshot,
                savedFilters = runtime.savedFilters.snapshot,
                currentAppVersion = requireAppVersion(currentAppVersion),
                createdAt = parseInstant(createdAt),
            )
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun importBackup(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        payload: String,
        currentAppVersion: String,
        createdAt: String,
    ): Boolean {
        state.markImporting()
        return try {
            applyResult(
                flow.start(
                    identity = identity,
                    authentication = authentication,
                    importedPayload = payload,
                    currentAppVersion = requireAppVersion(currentAppVersion),
                    createdAt = parseInstant(createdAt),
                    state = runtime.workspace,
                    settingsState = runtime.settings,
                    rulesState = runtime.messageRules,
                    filtersState = runtime.savedFilters,
                ),
            )
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun resumePendingImport(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: String,
    ): Boolean {
        val restored = try {
            transactions.restore()
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
        if (restored.pendingImport == null) return false

        state.markImporting()
        return try {
            applyResult(
                flow.resume(
                    identity = identity,
                    authentication = authentication,
                    currentAppVersion = requireAppVersion(currentAppVersion),
                    createdAt = parseInstant(createdAt),
                    state = runtime.workspace,
                    settingsState = runtime.settings,
                    rulesState = runtime.messageRules,
                    filtersState = runtime.savedFilters,
                ),
            )
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun keepLocal(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: String,
    ): Boolean {
        state.markResolving()
        return try {
            applyResult(
                flow.keepLocal(
                    identity = identity,
                    authentication = authentication,
                    currentAppVersion = requireAppVersion(currentAppVersion),
                    createdAt = parseInstant(createdAt),
                    state = runtime.workspace,
                    settingsState = runtime.settings,
                    rulesState = runtime.messageRules,
                    filtersState = runtime.savedFilters,
                ),
            )
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun useServer(authentication: StoredAuthentication): Boolean {
        state.markResolving()
        return try {
            val result = flow.useServer(
                authentication = authentication,
                state = runtime.workspace,
                settingsState = runtime.settings,
                rulesState = runtime.messageRules,
                filtersState = runtime.savedFilters,
            )
            state.markSynced(result.localApply.unresolvedLogins)
            true
        } catch (error: Throwable) {
            state.markFailed(error.message)
            throw error
        }
    }

    fun reportExported() {
        state.markIdle()
    }

    fun reportExportCancelled() {
        if (state.status == io.ferventio.shared.ui.app.SharedSettingsBackupStatus.EXPORTING) {
            state.markIdle()
        }
    }

    fun reportFileFailure(message: String?) {
        state.markFailed(message)
    }

    @Throws(Exception::class)
    fun discardPendingImport() {
        journal.clear()
        state.markIdle()
    }

    private fun applyResult(result: io.ferventio.shared.workspace.WorkspaceSettingsBackupImportFlowResult): Boolean {
        when (val sync = result.syncResult) {
            is WorkspaceSettingsBackupImportSyncResult.Synced -> {
                state.markSynced(result.localApply.unresolvedLogins)
            }
            is WorkspaceSettingsBackupImportSyncResult.Conflict -> {
                state.markConflict(
                    revision = sync.serverRevision,
                    unresolvedLogins = result.localApply.unresolvedLogins,
                )
            }
        }
        return true
    }

    private fun parseInstant(value: String): Instant = runCatching {
        Instant.parse(value.trim())
    }.getOrElse { error ->
        throw IllegalArgumentException("Invalid settings backup timestamp", error)
    }

    private fun requireAppVersion(value: String): String = value.trim().also { version ->
        require(version.isNotEmpty()) { "Current app version is unavailable" }
    }
}
