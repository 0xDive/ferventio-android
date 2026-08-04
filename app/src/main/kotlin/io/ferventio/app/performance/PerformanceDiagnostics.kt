package io.ferventio.app.performance

import android.os.StrictMode
import io.ferventio.app.BuildConfig

object PerformanceDiagnostics {
    val mainThreadWatchdog = MainThreadWatchdog()

    fun install() {
        mainThreadWatchdog.start()
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
