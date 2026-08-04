package io.ferventio.app.push

import android.app.Activity
import android.content.Context
import io.ferventio.app.FerventioApplication

object PlatformPushProviderFactory {
    fun create(context: Context): PlatformPushProvider = EmbeddedSocketPushProvider(context.applicationContext)
}

private class EmbeddedSocketPushProvider(
    private val context: Context,
) : PlatformPushProvider {
    override val transport: PushTransport = PushTransport.EMBEDDED_SOCKET
    override val isConfigured: Boolean = true

    override fun register(activity: Activity?, vapidPublicKey: String?) {
        coordinator().onPlatformRegistration(PlatformPushRegistration.EmbeddedSocket)
    }

    override fun activate() {
        FerventioPushConnectionService.start(context).onFailure { error ->
            coordinator().onProviderTemporarilyUnavailable(
                "Android не запустил фоновую службу: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    override fun unregister() {
        FerventioPushConnectionService.stop(context)
    }

    private fun coordinator(): PushCoordinator =
        (context.applicationContext as FerventioApplication).container.pushCoordinator
}
