package io.ferventio.app.twitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchUnofficialChattersParserTest {
    @Test
    fun `preserves chatter groups and deduplicates users`() {
        val body = """
            {
              "data": {
                "user": {
                  "channel": {
                    "chatters": {
                      "broadcasters": [{"id":"1","login":"owner"}],
                      "staff": [],
                      "vips": [{"id":"2","login":"vip"}],
                      "moderators": [{"id":"3","login":"mod"}],
                      "chatbots": [{"id":"5","login":"bot"}],
                      "viewers": [
                        {"id":"4","login":"viewer"},
                        {"id":"3","login":"mod"}
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val users = TwitchUnofficialChattersParser.parse(body)

        assertEquals(listOf("owner", "vip", "mod", "bot", "viewer"), users.map { it.login })
        assertEquals(listOf("1", "2", "3", "5", "4"), users.map { it.id })
        assertEquals(
            listOf(
                TwitchUnofficialChatterGroup.BROADCASTER,
                TwitchUnofficialChatterGroup.VIP,
                TwitchUnofficialChatterGroup.MODERATOR,
                TwitchUnofficialChatterGroup.CHATBOT,
                TwitchUnofficialChatterGroup.VIEWER,
            ),
            users.map { it.group },
        )
    }

    @Test
    fun `deduplicates mixed id entries by login and preserves higher role`() {
        val body = """
            {
              "data": {
                "user": {
                  "channel": {
                    "chatters": {
                      "broadcasters": [{"login":"Owner"}],
                      "staff": [],
                      "vips": [],
                      "moderators": [],
                      "chatbots": [],
                      "viewers": [{"id":"1","login":"owner"}]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val users = TwitchUnofficialChattersParser.parse(body)

        assertEquals(1, users.size)
        assertEquals("Owner", users.single().login)
        assertEquals("1", users.single().id)
        assertEquals(TwitchUnofficialChatterGroup.BROADCASTER, users.single().group)
    }

    @Test
    fun `parses CommunityTab batched response without chatter ids`() {
        val body = """
            [{
              "data": {
                "user": {
                  "channel": {
                    "chatters": {
                      "broadcasters": [{"login":"owner"}],
                      "chatbots": [{"login":"bot"}],
                      "moderators": [{"login":"mod"}],
                      "vips": [{"login":"vip"}],
                      "staff": [],
                      "viewers": [{"login":"viewer"}, {"login":"mod"}],
                      "count": 6
                    }
                  }
                }
              }
            }]
        """.trimIndent()

        val users = TwitchUnofficialChattersParser.parse(body)

        assertEquals(listOf("owner", "vip", "mod", "bot", "viewer"), users.map { it.login })
        assertTrue(users.all { it.id.isEmpty() })
        assertEquals(TwitchUnofficialChatterGroup.MODERATOR, users.first { it.login == "mod" }.group)
    }

    @Test
    fun `missing channel returns empty list`() {
        val users = TwitchUnofficialChattersParser.parse("""{"data":{"user":null}}""")

        assertTrue(users.isEmpty())
    }

    @Test(expected = TwitchUnofficialChattersException::class)
    fun `graphql errors fail explicitly`() {
        TwitchUnofficialChattersParser.parse(
            """[{"errors":[{"message":"chatters field unavailable"}]}]""",
        )
    }
}
