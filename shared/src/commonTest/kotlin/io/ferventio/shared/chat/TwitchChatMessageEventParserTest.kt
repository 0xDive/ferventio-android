package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchChatMessageEventParserTest {
    @Test
    fun parsesCanonicalChatMessageAndEventSubId() {
        val message = TwitchChatMessageEventParser.parse(
            TwitchEventSubProtocolParser.parse(
                """
                {
                  "metadata": {
                    "message_id": "eventsub-message-1",
                    "message_type": "notification",
                    "message_timestamp": "2026-07-21T10:00:00Z"
                  },
                  "payload": {
                    "subscription": {"type": "channel.chat.message"},
                    "event": {
                      "broadcaster_user_id": "100",
                      "broadcaster_user_login": "channel",
                      "chatter_user_id": "200",
                      "chatter_user_login": "viewer",
                      "chatter_user_name": "Viewer",
                      "message_id": "message-1",
                      "message_type": "text",
                      "color": "#00FF00",
                      "badges": [{"set_id": "moderator", "id": "1", "info": ""}],
                      "message": {"text": "Hello Ferventio"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        requireNotNull(message)
        assertEquals("eventsub-message-1", message.eventSubMessageId)
        assertEquals("message-1", message.id)
        assertEquals("100", message.channelId)
        assertEquals("Viewer", message.userDisplayName)
        assertEquals("Hello Ferventio", message.text)
        assertEquals("moderator", message.badges.single().setId)
        assertEquals(ChatMessageType.CHAT, message.type)
    }

    @Test
    fun preservesFragmentsReplyFlagsAndCheerClassification() {
        val message = TwitchChatMessageEventParser.parse(
            TwitchEventSubProtocolParser.parse(
                """
                {
                  "metadata": {
                    "message_id": "eventsub-message-2",
                    "message_type": "notification",
                    "message_timestamp": "2026-07-21T18:00:00Z"
                  },
                  "payload": {
                    "subscription": {"type": "channel.chat.message"},
                    "event": {
                      "broadcaster_user_id": "100",
                      "broadcaster_user_login": "channel",
                      "chatter_user_id": "200",
                      "chatter_user_login": "viewer",
                      "chatter_user_name": "Viewer",
                      "message_id": "message-fragments",
                      "message_type": "text",
                      "first_message": true,
                      "returning_chatter": false,
                      "badges": [],
                      "reply": {
                        "parent_message_id": "parent-1",
                        "parent_message_body": "Parent text",
                        "parent_user_id": "300",
                        "parent_user_login": "parent",
                        "parent_user_name": "Parent"
                      },
                      "message": {
                        "text": "Hello Kappa @friend Cheer100",
                        "fragments": [
                          {"type": "text", "text": "Hello "},
                          {"type": "emote", "text": "Kappa", "emote": {
                            "id": "25", "emote_set_id": "0", "owner_id": "twitch", "format": ["static"]
                          }},
                          {"type": "text", "text": " "},
                          {"type": "mention", "text": "@friend", "mention": {
                            "user_id": "400", "user_login": "friend", "user_name": "Friend"
                          }},
                          {"type": "text", "text": " "},
                          {"type": "cheermote", "text": "Cheer100", "cheermote": {
                            "prefix": "Cheer", "bits": 100, "tier": 100
                          }}
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        requireNotNull(message)
        assertEquals("parent-1", message.reply?.parentMessageId)
        assertTrue(message.flags.isFirstMessage)
        assertEquals(ChatMessageType.CHEER, message.type)
        assertTrue(message.fragments.any { it is ChatFragment.TwitchEmote })
        assertTrue(message.fragments.any { it is ChatFragment.Mention })
        assertTrue(message.fragments.any { it is ChatFragment.Cheermote })
    }

    @Test
    fun textFragmentWithUrlRemainsTextForRangeDetection() {
        val message = TwitchChatMessageEventParser.parse(
            TwitchEventSubProtocolParser.parse(
                """
                {
                  "metadata": {
                    "message_type": "notification",
                    "message_timestamp": "2026-08-02T10:00:00Z"
                  },
                  "payload": {
                    "subscription": {"type": "channel.chat.message"},
                    "event": {
                      "broadcaster_user_id": "100",
                      "broadcaster_user_login": "channel",
                      "chatter_user_id": "200",
                      "chatter_user_login": "viewer",
                      "message_id": "message-link-range",
                      "message_type": "text",
                      "badges": [],
                      "message": {
                        "text": "https://example.com/path this stays plain",
                        "fragments": [
                          {"type": "text", "text": "https://example.com/path this stays plain"}
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        requireNotNull(message)
        assertTrue(message.fragments.single() is ChatFragment.Text)
        assertEquals("https://example.com/path this stays plain", message.fragments.single().text)
    }

    @Test
    fun ignoresOtherNotificationTypesAndRejectsMalformedPrimaryPayload() {
        assertNull(
            TwitchChatMessageEventParser.parse(
                TwitchEventSubProtocolEnvelope(
                    type = "notification",
                    subscriptionType = "channel.chat.notification",
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            TwitchChatMessageEventParser.parse(
                TwitchEventSubProtocolEnvelope(
                    type = "notification",
                    subscriptionType = "channel.chat.message",
                    eventPayload = null,
                ),
            )
        }
    }
}
