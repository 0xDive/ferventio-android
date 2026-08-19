@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("CAST_NEVER_SUCCEEDS")

package io.ferventio.shared.history

import io.ferventio.app.domain.ChatHistoryStore
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSTimeZone
import platform.Foundation.create

/** Durable iOS history store backed by an atomically replaced Application Support snapshot. */
class IosChatHistoryStore : ChatHistoryStore by createIosChatHistoryStoreDelegate()

private fun createIosChatHistoryStoreDelegate(): ChatHistoryStore = SnapshotChatHistoryStore(
    storage = FoundationChatHistorySnapshotStorage(),
    localDateResolver = FoundationChatHistoryLocalDateResolver(),
)

private class FoundationChatHistorySnapshotStorage : ChatHistorySnapshotStorage {
    private val fileManager = NSFileManager.defaultManager
    private val directoryPath = applicationSupportPath().trimEnd('/') + "/Ferventio"
    private val filePath = "$directoryPath/chat-history-v1.json"

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
        val written = (value as NSString).writeToFile(
            path = filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        check(written) { "Unable to persist chat history snapshot" }
    }

    override fun clear() {
        if (!fileManager.fileExistsAtPath(filePath)) return
        val removed = fileManager.removeItemAtPath(filePath, error = null)
        check(removed) { "Unable to clear chat history snapshot" }
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

    private fun applicationSupportPath(): String =
        (NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String)
            ?: error("Application Support directory is unavailable")
}

private class FoundationChatHistoryLocalDateResolver : ChatHistoryLocalDateResolver {
    private val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
        timeZone = NSTimeZone.localTimeZone
        lenient = false
    }
    private val calendar = NSCalendar.currentCalendar

    override fun resolve(value: String): Pair<Long, Long>? {
        if (!ISO_DATE.matches(value)) return null
        val start = formatter.dateFromString(value) ?: return null
        val next = calendar.dateByAddingUnit(
            unit = NSCalendarUnitDay,
            value = 1,
            toDate = start,
            options = 0u,
        ) ?: return null
        return start.timeIntervalSince1970.toEpochMillis() to next.timeIntervalSince1970.toEpochMillis()
    }

    private fun Double.toEpochMillis(): Long = (this * 1_000.0).toLong()

    private companion object {
        val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
