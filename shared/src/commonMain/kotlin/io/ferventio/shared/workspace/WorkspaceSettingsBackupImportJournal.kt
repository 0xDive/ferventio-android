package io.ferventio.shared.workspace

import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ferventio.shared.settings.SharedSettingsBackupInputGuard
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface WorkspaceSettingsBackupImportJournalStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

internal data class WorkspaceSettingsBackupImportJournalSnapshot(
    val preImportPayload: String?,
    val pendingImport: WorkspaceSettingsPreparedImport?,
)

/**
 * Durable transaction metadata for settings import.
 *
 * The storage contract intentionally writes one record containing both the rollback source and the
 * pending local import. Platform implementations can therefore replace the journal atomically.
 */
internal class WorkspaceSettingsBackupImportJournal(
    private val storage: WorkspaceSettingsBackupImportJournalStorage,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun begin(
        preImportPayload: String?,
        importedPayload: String,
    ): WorkspaceSettingsBackupImportJournalSnapshot {
        val preparedImport = WorkspaceSettingsBackupImportPreparation.prepare(importedPayload)
        val validatedPreImport = preImportPayload
            ?.takeIf { it.isNotBlank() }
            ?.also { SharedSettingsBackupCodec.decode(it) }
        writeRecord(
            JournalRecord(
                preImportPayload = validatedPreImport,
                pendingImportPayload = importedPayload,
            ),
        )
        return WorkspaceSettingsBackupImportJournalSnapshot(
            preImportPayload = validatedPreImport,
            pendingImport = preparedImport,
        )
    }

    fun load(): WorkspaceSettingsBackupImportJournalSnapshot {
        val raw = storage.read() ?: return WorkspaceSettingsBackupImportJournalSnapshot(
            preImportPayload = null,
            pendingImport = null,
        )
        require(raw.encodeToByteArray().size <= MAX_JOURNAL_BYTES) {
            "Settings import journal exceeds the supported size"
        }
        val record = runCatching { json.decodeFromString<JournalRecord>(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid settings import journal", it) }
        require(record.schemaVersion == JOURNAL_SCHEMA_VERSION) {
            "Unsupported settings import journal version: ${record.schemaVersion}"
        }
        val preImport = record.preImportPayload
            ?.takeIf { it.isNotBlank() }
            ?.also { SharedSettingsBackupCodec.decode(it) }
        val pending = record.pendingImportPayload
            ?.takeIf { it.isNotBlank() }
            ?.let(WorkspaceSettingsBackupImportPreparation::prepare)
        return WorkspaceSettingsBackupImportJournalSnapshot(
            preImportPayload = preImport,
            pendingImport = pending,
        )
    }

    /** Marks sync/conflict resolution complete while retaining the latest rollback source. */
    fun markSettled(): WorkspaceSettingsBackupImportJournalSnapshot {
        val current = load()
        val preImport = current.preImportPayload
        if (preImport == null) {
            storage.clear()
        } else {
            writeRecord(JournalRecord(preImportPayload = preImport))
        }
        return WorkspaceSettingsBackupImportJournalSnapshot(
            preImportPayload = preImport,
            pendingImport = null,
        )
    }

    fun clear() {
        storage.clear()
    }

    private fun writeRecord(record: JournalRecord) {
        val encoded = json.encodeToString(record)
        require(encoded.encodeToByteArray().size <= MAX_JOURNAL_BYTES) {
            "Settings import journal exceeds the supported size"
        }
        storage.write(encoded)
    }

    @Serializable
    private data class JournalRecord(
        val schemaVersion: Int = JOURNAL_SCHEMA_VERSION,
        val preImportPayload: String? = null,
        val pendingImportPayload: String? = null,
    )

    private companion object {
        const val JOURNAL_SCHEMA_VERSION = 1
        const val MAX_JOURNAL_BYTES = SharedSettingsBackupInputGuard.MAX_BACKUP_FILE_BYTES * 4 + 4_096
    }
}
