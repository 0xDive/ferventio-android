package io.ferventio.app.push

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.ferventio.app.FerventioApplication
import io.ferventio.app.firebase.FirebaseAppProvider

object PlatformPushProviderFactory {
    fun create(context: Context): PlatformPushProvider = FcmPushProvider(context.applicationContext)
}

private class FcmPushProvider(
    private val context: Context,
) : PlatformPushProvider {
    override val transport: PushTransport = PushTransport.FCM

    override val isConfigured: Boolean
        get() = FirebaseAppProvider.isConfigured

    override fun register(activity: Activity?, vapidPublicKey: String?) {
        FirebaseAppProvider.getOrInitialize(context) ?: return coordinator().onProviderError(
            "Firebase не настроен для play-сборки",
        )
        FirebaseMessaging.getInstance()
            .register()
            .addOnFailureListener { error ->
                coordinator().onProviderError(
                    "FCM-регистрация не выполнена: ${error.message ?: error::class.simpleName}",
                )
            }
    }

    override fun activate() = Unit

    override fun unregister() {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: return
        FirebaseMessaging.getInstance()
            .unregister()
            .addOnFailureListener { error ->
                coordinator().onProviderError(
                    "FCM не отключён: ${error.message ?: error::class.simpleName}",
                )
            }
    }

    private fun coordinator(): PushCoordinator =
        (context.applicationContext as FerventioApplication).container.pushCoordinator
}
