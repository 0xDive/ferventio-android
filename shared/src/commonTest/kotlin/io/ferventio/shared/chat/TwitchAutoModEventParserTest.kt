package io.ferventio.shared.chat

import io.ferventio.app.domain.AutoModMessageStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TwitchAutoModEventParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesHeldMessageWithCategoryAndBoundaries() {
        val event = json.parseToJsonElement(
            """
            {
              "broadcaster_user_id":"channel-id",
              "broadcaster_user_login":"channel",
              "broadcaster_user_name":"Channel",
              "user_id":"viewer-id",
              "user_login":"viewer",
              "user_name":"Viewer",
              "message_id":"message-id",
              "message":{"text":"bad words"},
              "automod":{
                "category":"aggression",
                "level":2,
                "matches":[{"start_pos":0,"end_pos":2}]
              },
              "held_at":"2026-09-19T12:00:00Z"
            }
            """.trimIndent(),
        ).jsonObject

        val parsed = assertIs<TwitchAutoModEvent.Held>(
            TwitchAutoModEventParser.parse(
                TwitchEventSubProtocolEnvelope(
                    type = "notification",
                    messageTimestamp = "2026-09-19T12:00:01Z",
                    subscriptionType = "automod.message.hold",
                    eventPayload = event,
                ),
            ),
        )

        assertEquals("message-id", parsed.message.messageId)
        assertEquals("bad words", parsed.message.text)
        assertEquals("aggression", parsed.message.category)
        assertEquals(2, parsed.message.level)
        assertEquals(0, parsed.message.boundaries.single().start)
        assertEquals(2, parsed.message.boundaries.single().endInclusive)
        assertEquals(AutoModMessageStatus.HELD, parsed.message.status)
    }

    @Test
    fun expiredUpdateMapsToDistinctTerminalStatus() {
        val event = json.parseToJsonElement(
            """
            {
              "broadcaster_user_id":"channel-id",
              "user_id":"viewer-id",
              "user_login":"viewer",
              "message_id":"message-expired",
              "message":{"text":"expired"},
              "status":"expired"
            }
            """.trimIndent(),
        ).jsonObject

        val parsed = assertIs<TwitchAutoModEvent.Updated>(
            TwitchAutoModEventParser.parse(
                TwitchEventSubProtocolEnvelope(
                    type = "notification",
                    subscriptionType = "automod.message.update",
                    eventPayload = event,
                ),
            ),
        )

        assertEquals(AutoModMessageStatus.EXPIRED, parsed.message.status)
    }

    @Test
    fun terminalUpdateMapsApprovedAndModeratorIdentity() {
        val event = json.parseToJsonElement(
            """
            {
              "broadcaster_user_id":"channel-id",
              "user_id":"viewer-id",
              "user_login":"viewer",
              "message_id":"message-id",
              "message":{"text":"reviewed"},
              "status":"approved",
              "moderator_user_id":"mod-id",
              "moderator_user_login":"mod",
              "moderator_user_name":"Moderator"
            }
            """.trimIndent(),
        ).jsonObject

        val parsed = assertIs<TwitchAutoModEvent.Updated>(
            TwitchAutoModEventParser.parse(
                TwitchEventSubProtocolEnvelope(
                    type = "notification",
                    subscriptionType = "automod.message.update",
                    eventPayload = event,
                ),
            ),
        )

        assertEquals(AutoModMessageStatus.APPROVED, parsed.message.status)
        assertEquals("mod-id", parsed.message.decidedByUserId)
        assertEquals("Moderator", parsed.message.decidedByUserName)
    }
}
