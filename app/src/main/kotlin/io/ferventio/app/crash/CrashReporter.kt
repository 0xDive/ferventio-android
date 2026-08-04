package io.ferventio.app.crash

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler

internal object CrashReporter {
    fun install(context: Context) {
        runCatching { PlatformCrashReporter.install(context.applicationContext) }
    }

    fun breadcrumb(priority: Int, tag: String, message: String) {
        if (message.isBlank()) return
        runCatching {
            PlatformCrashReporter.breadcrumb("$priority/$tag ${message.take(MAX_BREADCRUMB_LENGTH)}")
        }
    }

    fun recordNonFatal(component: String, error: Throwable) {
        runCatching {
            PlatformCrashReporter.recordNonFatal(CrashReportSanitizer.sanitize(component, error))
        }
    }

    fun exportLocalReports(): LocalCrashReportExport =
        runCatching(PlatformCrashReporter::exportLocalReports)
            .getOrElse {
                LocalCrashReportExport(
                    content = LocalCrashReportCodec.encodeBundle(
                        LocalCrashReportBundle(
                            generatedAtEpochMillis = System.currentTimeMillis(),
                            reports = emptyList(),
                        ),
                    ),
                    reportCount = 0,
                )
            }

    fun clearLocalReports(): Int =
        runCatching(PlatformCrashReporter::clearLocalReports).getOrDefault(0)

    fun coroutineExceptionHandler(component: String): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, error -> recordNonFatal(component, error) }

    private const val MAX_BREADCRUMB_LENGTH = 1_024
}
