package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TwitchPinnedChatSnapshotParserTest {
    @Test
    fun parsesPublicPinnedMessageSnapshot() {
        val body = """
            [{
              "data": {
                "channel": {
                  "id": "channel-id",
                  "pinnedChatMessages": {
                    "edges": [{
                      "node": {
                        "startsAt": "2026-09-19T10:00:00Z",
                        "endsAt": null,
                        "pinnedBy": {"displayName": "Mod"},
                        "pinnedMessage": {
                          "id": "message-id",
                          "sender": {"id":"user-id","login":"viewer","displayName":"Viewer"},
                          "content": {
                            "text": "hello Kappa",
                            "fragments": [
                              {"type":"text","text":"hello "},
                              {"type":"emote","text":"Kappa","emote":{"id":"25","format":["static"]}}
                            ]
                          }
                        }
                      }
                    }]
                  }
                }
              }
            }]
        """.trimIndent()

        val pinned = requireNotNull(TwitchPinnedChatSnapshotParser.parse(body, "channel-id"))

        assertEquals("message-id", pinned.messageId)
        assertEquals("Viewer", pinned.senderUserName)
        assertEquals("Mod", pinned.pinnedByUserName)
        assertEquals("hello Kappa", pinned.text)
        assertIs<ChatFragment.TwitchEmote>(pinned.fragments.last())
    }

    @Test
    fun emptyEdgesMeanNoPinnedMessage() {
        val body = """[{"data":{"channel":{"id":"channel-id","pinnedChatMessages":{"edges":[]}}}}]"""
        assertNull(TwitchPinnedChatSnapshotParser.parse(body, "channel-id"))
    }
}
