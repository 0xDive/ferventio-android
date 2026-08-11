package io.ferventio.app.twitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwitchModerationPayloadTest {
    @Test
    fun `permanent ban omits duration and reason`() {
        val data = buildBanOrTimeoutPayload(
            targetUserId = "target",
            durationSeconds = null,
            reason = "Ferventio moderation",
        )["data"]!!.jsonObject

        assertEquals("target", data["user_id"]!!.jsonPrimitive.content)
        assertFalse("duration" in data)
        assertFalse("reason" in data)
    }

    @Test
    fun `timeout keeps explicit reason`() {
        val data = buildBanOrTimeoutPayload(
            targetUserId = "target",
            durationSeconds = 600,
            reason = "spam",
        )["data"]!!.jsonObject

        assertEquals("600", data["duration"]!!.jsonPrimitive.content)
        assertEquals("spam", data["reason"]!!.jsonPrimitive.content)
    }
}
