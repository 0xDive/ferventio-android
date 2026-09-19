package io.ferventio.app.push

typealias PushRegistrationContext = io.ferventio.shared.push.PushRegistrationContext
typealias PushRegistrationRequest = io.ferventio.shared.push.PushRegistrationRequest
typealias PushNotificationPayload = io.ferventio.shared.push.PushNotificationPayload
typealias PushSocketClientMessage = io.ferventio.shared.push.PushSocketClientMessage
typealias PushSocketServerMessage = io.ferventio.shared.push.PushSocketServerMessage

enum class PushTransport(val wireName: String, val displayName: String) {
    FCM("fcm", "Firebase Cloud Messaging"),
    EMBEDDED_SOCKET("embedded_socket", "Автономный Ferventio Push"),
}

enum class PushStatus {
    DISABLED,
    NEEDS_CONFIGURATION,
    REGISTERING,
    CONNECTING,
    ACTIVE,
    TEMPORARILY_UNAVAILABLE,
    ERROR,
}

data class PushUiState(
    val transport: PushTransport,
    val serverUrl: String,
    val enabled: Boolean,
    val providerConfigured: Boolean,
    val status: PushStatus,
    val detail: String = "",
    val lastMessageAtMillis: Long? = null,
    val lastHeartbeatAtMillis: Long? = null,
    val lastConnectedAtMillis: Long? = null,
    val reconnectAttempt: Int = 0,
    val foregroundServiceRequired: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface PlatformPushRegistration {
    val transport: PushTransport

    data class FirebaseInstallation(
        val fid: String,
    ) : PlatformPushRegistration {
        override val transport: PushTransport = PushTransport.FCM
    }

    data object EmbeddedSocket : PlatformPushRegistration {
        override val transport: PushTransport = PushTransport.EMBEDDED_SOCKET
    }
}
