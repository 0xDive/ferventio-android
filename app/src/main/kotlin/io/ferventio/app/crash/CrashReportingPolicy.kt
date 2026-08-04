package io.ferventio.app.crash

internal object CrashReportingPolicy {
    fun shouldCollect(
        isDebug: Boolean,
        performanceTesting: Boolean,
        firebaseConfigured: Boolean,
    ): Boolean = firebaseConfigured && !isDebug && !performanceTesting
}
