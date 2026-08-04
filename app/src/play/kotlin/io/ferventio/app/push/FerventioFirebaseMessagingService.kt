package io.ferventio.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.ferventio.app.FerventioApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FerventioFirebaseMessagingService : FirebaseMessagingService() {
    private val coordinator: PushCoordinator
        get() = (application as FerventioApplication).container.pushCoordinator

    override fun onRegistered(installationId: String) {
        coordinator.onPlatformRegistration(
            PlatformPushRegistration.FirebaseInstallation(installationId),
        )
    }

    override fun onUnregistered(installationId: String) {
        coordinator.onProviderUnregistered()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val payload = remoteMessage.data[PAYLOAD_KEY]?.toByteArray()
            ?: Json.encodeToString(
                PushNotificationPayload(
                    title = remoteMessage.notification?.title ?: "Ferventio",
                    body = remoteMessage.notification?.body
                        ?: remoteMessage.data["body"]
                        ?: "Новое событие в чате",
                    channelId = remoteMessage.data["channelId"],
                    channelLogin = remoteMessage.data["channelLogin"],
                    messageId = remoteMessage.data["messageId"],
                ),
            ).toByteArray()
        coordinator.onPushPayload(payload)
    }

    override fun onDeletedMessages() {
        coordinator.onProviderTemporarilyUnavailable(
            "FCM удалил часть накопленных сообщений; требуется синхронизация с сервером",
        )
    }

    private companion object {
        const val PAYLOAD_KEY = "payload"
    }
}
