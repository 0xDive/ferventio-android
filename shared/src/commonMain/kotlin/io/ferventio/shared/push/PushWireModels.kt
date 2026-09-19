package io.ferventio.shared.push

import kotlinx.serialization.Serializable

/** Cross-platform notification payload contract delivered by Ferventio push backends. */
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

/** Client-to-server wire messages for the embedded push socket transport. */
@Serializable
data class PushSocketClientMessage(
    val type: String,
    val protocolVersion: Int = 1,
    val installationId: String? = null,
    val deviceSecret: String? = null,
    val lastEventId: String? = null,
    val eventId: String? = null,
)

/** Server-to-client wire messages for the embedded push socket transport. */
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
