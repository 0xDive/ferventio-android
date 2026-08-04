package io.ferventio.app.crash

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LocalCrashReport(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val createdAtEpochMillis: Long,
    val fatal: Boolean,
    val threadName: String,
    val summary: String,
    val stackTrace: List<String>,
    val breadcrumbs: List<String>,
    val appVersionName: String,
    val appVersionCode: Int,
    val buildType: String,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
internal data class LocalCrashReportBundle(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val generatedAtEpochMillis: Long,
    val reports: List<LocalCrashReport>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

internal data class LocalCrashReportExport(
    val content: String,
    val reportCount: Int,
)

internal object LocalCrashReportCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encodeReport(report: LocalCrashReport): String = json.encodeToString(report)

    fun decodeReport(content: String): LocalCrashReport = json.decodeFromString(content)

    fun encodeBundle(bundle: LocalCrashReportBundle): String = json.encodeToString(bundle)
}

internal object LocalCrashReportRetention {
    fun retainNewest(
        reports: List<LocalCrashReport>,
        nowEpochMillis: Long,
        maxAgeMillis: Long,
        maxReports: Int,
    ): List<LocalCrashReport> = reports
        .asSequence()
        .filter { report ->
            report.createdAtEpochMillis in (nowEpochMillis - maxAgeMillis)..nowEpochMillis
        }
        .sortedByDescending(LocalCrashReport::createdAtEpochMillis)
        .take(maxReports.coerceAtLeast(0))
        .toList()
}
