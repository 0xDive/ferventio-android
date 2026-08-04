package io.ferventio.app.crash

import android.content.Context
import io.ferventio.app.BuildConfig

internal object PlatformCrashReporter {
    @Volatile
    private var store: LocalCrashReportStore? = null

    fun install(context: Context) {
        if (BuildConfig.PERFORMANCE_TESTING) return
        val localStore = LocalCrashReportStore(context)
        store = localStore
        installSanitizingUncaughtHandler(localStore)
    }

    fun breadcrumb(message: String) {
        store?.breadcrumb(message)
    }

    fun recordNonFatal(error: Throwable) {
        store?.record(
            fatal = false,
            threadName = Thread.currentThread().name,
            error = error,
        )
    }

    fun exportLocalReports(): LocalCrashReportExport =
        store?.export() ?: LocalCrashReportExport(
            content = LocalCrashReportCodec.encodeBundle(
                LocalCrashReportBundle(
                    generatedAtEpochMillis = System.currentTimeMillis(),
                    reports = emptyList(),
                ),
            ),
            reportCount = 0,
        )

    fun clearLocalReports(): Int = store?.clear() ?: 0

    @Synchronized
    private fun installSanitizingUncaughtHandler(localStore: LocalCrashReportStore) {
        if (uncaughtHandlerInstalled) return
        val downstream = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val sanitized = CrashReportSanitizer.sanitize("uncaught_exception", error)
            localStore.record(
                fatal = true,
                threadName = thread.name,
                error = sanitized,
            )
            downstream.uncaughtException(thread, sanitized)
        }
        uncaughtHandlerInstalled = true
    }

    @Volatile
    private var uncaughtHandlerInstalled: Boolean = false
}
