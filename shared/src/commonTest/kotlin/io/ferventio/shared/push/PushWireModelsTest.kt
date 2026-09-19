package io.ferventio.shared.push

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PushWireModelsTest {
    @Test
    fun notificationPayloadRoundTripsAcrossSharedSerialization() {
        val source = PushNotificationPayload(
            eventId = "event-1",
            type = "moderation",
            title = "Ferventio",
            body = "Moderator alert",
            channelId = "123",
            channelLogin = "channel",
            messageId = "message-1",
            actorId = "author-1",
            actorLogin = "author",
            actorDisplayName = "Author",
            destination = "moderation",
            silent = true,
            createdAtEpochMillis = 1_234L,
        )

        assertEquals(
            source,
            Json.decodeFromString<PushNotificationPayload>(Json.encodeToString(source)),
        )
    }

    @Test
    fun embeddedSocketServerMessageRoundTripsWithNestedPayload() {
        val source = PushSocketServerMessage(
            type = "event",
            connectionId = "connection-1",
            heartbeatSeconds = 30,
            eventId = "event-1",
            payload = PushNotificationPayload(body = "hello", channelId = "123"),
            serverTimeEpochMillis = 9_999L,
        )

        assertEquals(
            source,
            Json.decodeFromString<PushSocketServerMessage>(Json.encodeToString(source)),
        )
    }
}
