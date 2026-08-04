package io.ferventio.app.push

import kotlinx.serialization.Serializable

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

data class PushRegistrationContext(
    val userId: String? = null,
    val userLogin: String? = null,
    val channelIds: List<String> = emptyList(),
    val moderatorChannelIds: List<String> = emptyList(),
    val notificationRules: List<String> = DEFAULT_NOTIFICATION_RULES,
    val highlightPhrases: List<String> = emptyList(),
    val selectedUserLogins: List<String> = emptyList(),
) {
    companion object {
        val DEFAULT_NOTIFICATION_RULES = listOf(
            "mention",
            "reply",
            "automod_hold",
            "ban",
            "timeout",
            "highlight",
            "selected_user",
            "stream_online",
            "title_change",
            "game_change",
            "raid",
            "reward",
            "subscription",
            "moderation_action",
        )
    }
}

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

@Serializable
data class PushRegistrationRequest(
    val installationId: String,
    val deviceSecret: String,
    val provider: String,
    val firebaseInstallationId: String? = null,
    val appVersion: String,
    val platform: String,
    val userId: String? = null,
    val userLogin: String? = null,
    val channelIds: List<String> = emptyList(),
    val moderatorChannelIds: List<String> = emptyList(),
    val notificationRules: List<String> = emptyList(),
    val highlightPhrases: List<String> = emptyList(),
    val selectedUserLogins: List<String> = emptyList(),
)

@Serializable
data class PushNotificationPayload(
    val eventId: String? = null,
    val type: String = "generic",
    val title: String = "Ferventio",
    val body: String,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val messageId: String? = null,
    val actorId: String? = null,
    val actorLogin: String? = null,
    val actorDisplayName: String? = null,
    val destination: String? = null,
    val silent: Boolean = false,
    val createdAtEpochMillis: Long? = null,
)

@Serializable
data class PushSocketClientMessage(
    val type: String,
    val protocolVersion: Int = 1,
    val installationId: String? = null,
    val deviceSecret: String? = null,
    val lastEventId: String? = null,
    val eventId: String? = null,
)

@Serializable
data class PushSocketServerMessage(
    val type: String,
    val connectionId: String? = null,
    val heartbeatSeconds: Int? = null,
    val eventId: String? = null,
    val payload: PushNotificationPayload? = null,
    val message: String? = null,
    val serverTimeEpochMillis: Long? = null,
)
