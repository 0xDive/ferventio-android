package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TwitchPinnedChatGqlParserTest {
    @Test
    fun `empty edges means channel has no pinned message`() {
        val result = TwitchPinnedChatGqlParser.parse(
            body = """
                [{
                  "data": {
                    "channel": {
                      "id": "144209695",
                      "pinnedChatMessages": {"edges": []}
                    }
                  }
                }]
            """.trimIndent(),
            requestedChannelId = "144209695",
        )

        assertNull(result)
    }

    @Test
    fun `mod pin is parsed for an ordinary viewer`() {
        val result = TwitchPinnedChatGqlParser.parse(
            body = """
                [{
                  "data": {
                    "channel": {
                      "id": "144209695",
                      "pinnedChatMessages": {
                        "edges": [{
                          "node": {
                            "id": "pin-1",
                            "type": "MOD",
                            "pinnedMessage": {
                              "id": "message-1",
                              "content": {
                                "text": "Не спамим и не флудим",
                                "fragments": [
                                  {"content": null, "text": "Не спамим и не флудим"}
                                ]
                              },
                              "sender": {
                                "id": "535024028",
                                "login": "nagliykot_",
                                "displayName": "NagliyKot_"
                              }
                            },
                            "startsAt": "2026-08-02T09:40:33Z",
                            "updatedAt": "2026-08-02T09:40:42Z",
                            "endsAt": null,
                            "pinnedBy": {
                              "id": "535024028",
                              "login": "nagliykot_",
                              "displayName": "NagliyKot_"
                            }
                          }
                        }]
                      }
                    }
                  }
                }]
            """.trimIndent(),
            requestedChannelId = "fallback-channel",
        )

        requireNotNull(result)
        assertEquals("144209695", result.channelId)
        assertEquals("message-1", result.messageId)
        assertEquals("535024028", result.senderUserId)
        assertEquals("nagliykot_", result.senderUserLogin)
        assertEquals("NagliyKot_", result.senderUserName)
        assertEquals("NagliyKot_", result.pinnedByUserName)
        assertEquals("Не спамим и не флудим", result.text)
        assertEquals(listOf(ChatFragment.Text("Не спамим и не флудим")), result.fragments)
        assertEquals("2026-08-02T09:40:33Z", result.startsAt)
        assertNull(result.endsAt)
    }

    @Test
    fun `graphql error does not look like an empty pin`() {
        val error = assertThrows(TwitchPinnedChatGqlException::class.java) {
            TwitchPinnedChatGqlParser.parse(
                body = """[{"errors":[{"message":"PersistedQueryNotFound"}]}]""",
                requestedChannelId = "144209695",
            )
        }

        assertEquals("PersistedQueryNotFound", error.message)
    }
}
