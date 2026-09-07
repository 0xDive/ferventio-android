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
        rollbackPayload: (String?) -> String? = { remotePayload -> remotePayload },
    ): WorkspaceSettingsBackupImportJournalSnapshot {
        // Validate before any remote read so a malformed file cannot create network side effects.
        WorkspaceSettingsBackupImportPreparation.prepare(importedPayload)
        val existing = journal.load()
        require(existing.pendingImport == null && existing.conflict == null) {
            "A settings import is already pending"
        }
        val current = snapshots.fetch(identity, authentication)
        return journal.begin(
            preImportPayload = rollbackPayload(current?.payload),
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
        val snapshot = journal.load()
        val pending = snapshot.pendingImport
            ?: error("No pending settings import is available")
        require(snapshot.conflict == null) {
            "Settings import conflict requires an explicit resolution"
        }
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
        val snapshot = journal.load()
        val pending = snapshot.pendingImport
            ?: error("No pending settings import is available")
        require(snapshot.conflict != null) {
            "Forced settings overwrite requires an unresolved conflict"
        }
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

    /** Settles a durable conflict only after the caller has successfully applied the server side. */
    fun acceptServerConflict(expectedServerRevision: Long) {
        require(expectedServerRevision > 0L) { "Settings conflict revision must be positive" }
        val conflict = journal.load().conflict
            ?: error("No settings import conflict is available")
        require(conflict.serverRevision == expectedServerRevision) {
            "Settings import conflict changed before it was settled"
        }
        journal.markSettled()
    }
}
