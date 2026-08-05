package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatChannel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchRecentMessagesParserTest {
    private val channel = ChatChannel(
        id = "channel-id",
        login = "channel",
        displayName = "Channel",
    )

    @Test
    fun `parses IRC messages and remaps them to requested channel`() {
        val first = "@display-name=Viewer;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :first"
        val second = "@display-name=Other;id=message-2;login=other;room-id=1234;" +
            "tmi-sent-ts=1720000001000;user-id=66 :other!other@other.tmi.twitch.tv " +
            "PRIVMSG #channel :second"
        val body = buildJsonObject {
            put("messages", buildJsonArray {
                add(JsonPrimitive(second))
                add(JsonPrimitive(first))
            })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 100)

        assertEquals(listOf("message-1", "message-2"), result.messages.map { it.id })
        assertTrue(result.messages.all { it.channelId == "channel-id" })
        assertTrue(result.messages.all { it.channelLogin == "channel" })
    }

    @Test
    fun `keeps the newest rows when service returns more than requested limit`() {
        val body = buildJsonObject {
            put("messages", buildJsonArray {
                (1..3).forEach { index ->
                    add(
                        JsonPrimitive(
                            "@display-name=Viewer;id=message-$index;login=viewer;room-id=1234;" +
                                "tmi-sent-ts=${1720000000000L + index};user-id=55 " +
                                ":viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :$index",
                        ),
                    )
                }
            })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 2)

        assertEquals(listOf("message-2", "message-3"), result.messages.map { it.id })
    }

    @Test
    fun `keeps service recovery error code while returning available messages`() {
        val raw = "@display-name=Viewer;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :hello"
        val body = buildJsonObject {
            put("error_code", JsonPrimitive("channel_not_joined"))
            put("messages", buildJsonArray { add(JsonPrimitive(raw)) })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 100)

        assertEquals("channel_not_joined", result.errorCode)
        assertEquals(listOf("message-1"), result.messages.map { it.id })
    }

    @Test
    fun `applies moderation events contained in the snapshot`() {
        val raw = "@display-name=Viewer;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :hello"
        val deleted = "@room-id=1234;target-msg-id=message-1;tmi-sent-ts=1720000001000 " +
            ":tmi.twitch.tv CLEARMSG #channel :hello"
        val body = buildJsonObject {
            put("messages", buildJsonArray {
                add(JsonPrimitive(raw))
                add(JsonPrimitive(deleted))
            })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 100)

        assertTrue(result.messages.single().isDeleted)
    }

    @Test
    fun `ignores moderation and malformed IRC rows`() {
        val body = buildJsonObject {
            put("messages", buildJsonArray {
                add(JsonPrimitive("@room-id=1234 :tmi.twitch.tv CLEARCHAT #channel"))
                add(JsonPrimitive("not an irc message"))
            })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 100)

        assertTrue(result.messages.isEmpty())
    }
}
