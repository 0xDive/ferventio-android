package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import kotlin.time.Instant

internal sealed interface WorkspaceSettingsBackupImportSyncResult {
    data class Synced(
        val revision: Long,
        val payload: String,
    ) : WorkspaceSettingsBackupImportSyncResult

    data class Conflict(
        val serverRevision: Long,
        val serverImport: WorkspaceSettingsPreparedImport,
    ) : WorkspaceSettingsBackupImportSyncResult
}

/**
 * Coordinates the durable network side of a settings import without mutating visible workspace UI.
 *
 * Local state application stays separate until imported channel logins can be resolved without
 * dropping unresolved channels. This coordinator only owns validation, rollback journaling,
 * optimistic upload, durable 409 state, and the explicit force path chosen by the user.
 */
internal class WorkspaceSettingsBackupImportCoordinator(
    private val snapshots: WorkspaceSettingsSyncClient,
    private val uploads: WorkspaceSettingsBackupUploadClient,
    private val journal: WorkspaceSettingsBackupImportJournal,
) {
    suspend fun begin(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        importedPayload: String,
    ): WorkspaceSettingsBackupImportJournalSnapshot {
        // Validate before any remote read so a malformed file cannot create network side effects.
        WorkspaceSettingsBackupImportPreparation.prepare(importedPayload)
        val current = snapshots.fetch(identity, authentication)
        return journal.begin(
            preImportPayload = current?.payload,
            importedPayload = importedPayload,
        )
    }

    fun restore(): WorkspaceSettingsBackupImportJournalSnapshot = journal.load()

    suspend fun syncPending(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: Instant,
    ): WorkspaceSettingsBackupImportSyncResult {
        val pending = journal.load().pendingImport
            ?: error("No pending settings import is available")
        return when (
            val result = uploads.upload(
                identity = identity,
                authentication = authentication,
                importedPayload = pending.sourcePayload,
                currentAppVersion = currentAppVersion,
                createdAt = createdAt,
            )
        ) {
            is WorkspaceSettingsBackupUploadResult.Success -> {
                journal.markSettled()
                WorkspaceSettingsBackupImportSyncResult.Synced(
                    revision = result.revision,
                    payload = result.payload,
                )
            }
            is WorkspaceSettingsBackupUploadResult.Conflict -> {
                val persisted = journal.markConflict(
                    serverRevision = result.serverRevision,
                    serverPayload = result.serverPayload,
                )
                val conflict = requireNotNull(persisted.conflict)
                WorkspaceSettingsBackupImportSyncResult.Conflict(
                    serverRevision = conflict.serverRevision,
                    serverImport = conflict.serverImport,
                )
            }
        }
    }

    /** Explicit Android-parity "Use local" conflict resolution. Never called automatically. */
    suspend fun keepLocal(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        currentAppVersion: String,
        createdAt: Instant,
    ): WorkspaceSettingsBackupImportSyncResult.Synced {
        val pending = journal.load().pendingImport
            ?: error("No pending settings import is available")
        val result = uploads.overwrite(
            identity = identity,
            authentication = authentication,
            importedPayload = pending.sourcePayload,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
        )
        journal.markSettled()
        return WorkspaceSettingsBackupImportSyncResult.Synced(
            revision = result.revision,
            payload = result.payload,
        )
    }
}
