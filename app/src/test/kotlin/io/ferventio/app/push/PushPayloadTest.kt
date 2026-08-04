package io.ferventio.app.push

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PushPayloadTest {
    @Test
    fun payloadRoundTrip() {
        val json = Json
        val source = PushNotificationPayload(
            title = "Ferventio",
            body = "Moderator alert",
            channelId = "123",
            channelLogin = "channel",
            messageId = "message-1",
            actorId = "author-1",
            actorLogin = "author",
            actorDisplayName = "Author",
        )

        val decoded = json.decodeFromString<PushNotificationPayload>(json.encodeToString(source))

        assertEquals(source, decoded)
    }
}
