package io.ferventio.app.crash

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.ferventio.app.BuildConfig
import io.ferventio.app.firebase.FirebaseAppProvider

internal object PlatformCrashReporter {
    @Volatile
    private var reporter: FirebaseCrashlytics? = null

    fun install(context: Context) {
        val firebaseApp = FirebaseAppProvider.getOrInitialize(context)
        val enabled = CrashReportingPolicy.shouldCollect(
            isDebug = BuildConfig.DEBUG,
            performanceTesting = BuildConfig.PERFORMANCE_TESTING,
            firebaseConfigured = firebaseApp != null,
        )
        if (firebaseApp == null) return

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) {
            reporter = null
            return
        }
        crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
        crashlytics.setCustomKey("push_transport", BuildConfig.PUSH_TRANSPORT)
        crashlytics.setCustomKey("performance_testing", BuildConfig.PERFORMANCE_TESTING)
        crashlytics.setCustomKey("version_name", BuildConfig.VERSION_NAME)
        installSanitizingUncaughtHandler()
        reporter = crashlytics
    }

    fun breadcrumb(message: String) {
        reporter?.log(message)
    }

    fun recordNonFatal(error: Throwable) {
        reporter?.recordException(error)
    }

    fun exportLocalReports(): LocalCrashReportExport = LocalCrashReportExport(
        content = LocalCrashReportCodec.encodeBundle(
            LocalCrashReportBundle(
                generatedAtEpochMillis = System.currentTimeMillis(),
                reports = emptyList(),
            ),
        ),
        reportCount = 0,
    )

    fun clearLocalReports(): Int = 0

    @Synchronized
    private fun installSanitizingUncaughtHandler() {
        if (uncaughtHandlerInstalled) return
        val downstream = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            downstream.uncaughtException(
                thread,
                CrashReportSanitizer.sanitize("uncaught_exception", error),
            )
        }
        uncaughtHandlerInstalled = true
    }

    @Volatile
    private var uncaughtHandlerInstalled: Boolean = false
}
