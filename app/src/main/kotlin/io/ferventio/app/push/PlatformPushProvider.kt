package io.ferventio.app.push

import android.app.Activity

interface PlatformPushProvider {
    val transport: PushTransport
    val isConfigured: Boolean

    fun register(activity: Activity?, vapidPublicKey: String?)
    fun activate()
    fun unregister()
}
