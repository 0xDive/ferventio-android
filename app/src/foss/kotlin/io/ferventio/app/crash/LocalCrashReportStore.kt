package io.ferventio.app.crash

import android.content.Context
import io.ferventio.app.BuildConfig
import io.ferventio.app.security.SensitiveDataRedactor
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

internal class LocalCrashReportStore(context: Context) {
    private val reportsDirectory = File(context.filesDir, REPORTS_DIRECTORY_NAME)
    private val breadcrumbs = ArrayDeque<String>(MAX_BREADCRUMBS)

    @Synchronized
    fun breadcrumb(message: String) {
        val normalized = SensitiveDataRedactor.redact(message)
            .orEmpty()
            .trim()
            .take(MAX_BREADCRUMB_LENGTH)
        if (normalized.isBlank()) return
        while (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        breadcrumbs.addLast(normalized)
    }

    @Synchronized
    fun record(fatal: Boolean, threadName: String, error: Throwable) {
        runCatching {
            reportsDirectory.mkdirs()
            val now = System.currentTimeMillis()
            val report = LocalCrashReport(
                id = UUID.randomUUID().toString(),
                createdAtEpochMillis = now,
                fatal = fatal,
                threadName = threadName.take(MAX_THREAD_NAME_LENGTH),
                summary = error.message.orEmpty().take(MAX_SUMMARY_LENGTH),
                stackTrace = error.stackTrace
                    .asSequence()
                    .take(MAX_STACK_FRAMES)
                    .map(StackTraceElement::toString)
                    .toList(),
                breadcrumbs = breadcrumbs.toList(),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
            )
            writeAtomically(report)
            prune(now)
        }
    }

    @Synchronized
    fun export(): LocalCrashReportExport {
        val now = System.currentTimeMillis()
        val reports = readReports()
        val retained = LocalCrashReportRetention.retainNewest(
            reports = reports,
            nowEpochMillis = now,
            maxAgeMillis = MAX_REPORT_AGE_MILLIS,
            maxReports = MAX_REPORTS,
        )
        prune(now)
        return LocalCrashReportExport(
            content = LocalCrashReportCodec.encodeBundle(
                LocalCrashReportBundle(
                    generatedAtEpochMillis = now,
                    reports = retained,
                ),
            ),
            reportCount = retained.size,
        )
    }

    @Synchronized
    fun clear(): Int {
        val files = reportsDirectory.listFiles().orEmpty().filter(File::isFile)
        var deleted = 0
        files.forEach { file ->
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun writeAtomically(report: LocalCrashReport) {
        val finalFile = File(reportsDirectory, "${report.createdAtEpochMillis}-${report.id}.json")
        val tempFile = File(reportsDirectory, ".${finalFile.name}.tmp")
        val content = LocalCrashReportCodec.encodeReport(report)
        tempFile.writeText(content, Charsets.UTF_8)
        if (!tempFile.renameTo(finalFile)) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun readReports(): List<LocalCrashReport> = reportsDirectory
        .listFiles { file -> file.isFile && file.extension == "json" }
        .orEmpty()
        .mapNotNull { file ->
            runCatching {
                require(file.length() <= MAX_REPORT_FILE_BYTES)
                LocalCrashReportCodec.decodeReport(file.readText(Charsets.UTF_8))
            }.getOrElse {
                file.delete()
                null
            }
        }

    private fun prune(nowEpochMillis: Long) {
        val retainedIds = LocalCrashReportRetention.retainNewest(
            reports = readReports(),
            nowEpochMillis = nowEpochMillis,
            maxAgeMillis = MAX_REPORT_AGE_MILLIS,
            maxReports = MAX_REPORTS,
        ).mapTo(hashSetOf(), LocalCrashReport::id)

        reportsDirectory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .forEach { file ->
                val id = file.nameWithoutExtension.substringAfter('-', missingDelimiterValue = "")
                if (id !in retainedIds) file.delete()
            }
    }

    private companion object {
        const val REPORTS_DIRECTORY_NAME = "foss-crash-reports"
        const val MAX_REPORTS = 20
        const val MAX_BREADCRUMBS = 100
        const val MAX_BREADCRUMB_LENGTH = 1_024
        const val MAX_THREAD_NAME_LENGTH = 128
        const val MAX_SUMMARY_LENGTH = 2_048
        const val MAX_STACK_FRAMES = 256
        const val MAX_REPORT_FILE_BYTES = 512L * 1_024L
        const val MAX_REPORT_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }
}
