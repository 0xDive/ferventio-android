@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("CAST_NEVER_SUCCEEDS")

package io.ferventio.shared.workspace

import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.posix.rename

/** Atomic Application Support persistence for the shared settings-import transaction journal. */
internal class IosWorkspaceSettingsBackupImportJournalStorage :
    WorkspaceSettingsBackupImportJournalStorage {
    private val fileManager = NSFileManager.defaultManager
    private val directoryPath = applicationSupportPath().trimEnd('/') + "/Ferventio"
    private val filePath = "$directoryPath/settings-import-journal-v1.json"
    private val temporaryFilePath = "$directoryPath/settings-import-journal-v1.tmp"

    init {
        ensureDirectory()
    }

    override fun read(): String? {
        if (!fileManager.fileExistsAtPath(filePath)) return null
        return runCatching {
            NSString.create(
                contentsOfFile = filePath,
                encoding = NSUTF8StringEncoding,
                error = null,
            ) as String
        }.getOrNull()
    }

    override fun write(value: String) {
        ensureDirectory()
        removeTemporaryFile()
        val written = fileManager.createFileAtPath(
            path = temporaryFilePath,
            contents = value.toUtf8Data(),
            attributes = null,
        )
        check(written) { "Unable to write settings import journal temporary file" }
        val replaced = rename(temporaryFilePath, filePath) == 0
        if (!replaced) removeTemporaryFile()
        check(replaced) { "Unable to atomically replace settings import journal" }
    }

    override fun clear() {
        removeTemporaryFile()
        if (!fileManager.fileExistsAtPath(filePath)) return
        val removed = fileManager.removeItemAtPath(filePath, error = null)
        check(removed) { "Unable to clear settings import journal" }
    }

    private fun ensureDirectory() {
        if (fileManager.fileExistsAtPath(directoryPath)) return
        val created = fileManager.createDirectoryAtPath(
            path = directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        check(created) { "Unable to create Ferventio Application Support directory" }
    }

    private fun removeTemporaryFile() {
        if (fileManager.fileExistsAtPath(temporaryFilePath)) {
            fileManager.removeItemAtPath(temporaryFilePath, error = null)
        }
    }

    private fun applicationSupportPath(): String =
        (NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String)
            ?: error("Application Support directory is unavailable")

    private fun String.toUtf8Data(): NSData = memScoped {
        val bytes = encodeToByteArray()
        NSData.create(
            bytes = allocArrayOf(bytes),
            length = bytes.size.toULong(),
        )
    }
}

internal fun createIosWorkspaceSettingsBackupImportJournal(): WorkspaceSettingsBackupImportJournal =
    WorkspaceSettingsBackupImportJournal(IosWorkspaceSettingsBackupImportJournalStorage())
