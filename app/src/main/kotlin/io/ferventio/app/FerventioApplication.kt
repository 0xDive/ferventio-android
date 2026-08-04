package io.ferventio.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.ferventio.app.crash.CrashReporter
import io.ferventio.app.data.FerventioImageLoader
import io.ferventio.app.performance.PerformanceDiagnostics

class FerventioApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(context: Context): ImageLoader =
        FerventioImageLoader.create(context)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        PerformanceDiagnostics.install()
        container.pushCoordinator.bootstrap()
        container.controller.bootstrap()
    }
}
