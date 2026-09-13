package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class TwitchRecentMessagesClientTest {
    private val channel = ChatChannel(
        id = "channel-id",
        login = "channel",
        displayName = "Channel",
    )

    @Test
    fun loadUsesValidatedLoginAndMapsSnapshotToRequestedChannel() = runTest {
        val raw = "@display-name=Viewer;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :hello"
        val body = buildJsonObject {
            put("messages", buildJsonArray { add(JsonPrimitive(raw)) })
        }.toString()
        val engine = MockEngine { request ->
            assertEquals("/api/v2/recent-messages/channel", request.url.encodedPath)
            assertEquals("100", request.url.parameters["limit"])
            assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
            respondJson(body)
        }
        val client = TwitchRecentMessagesClient(
            client = HttpClient(engine) { expectSuccess = false },
            baseUrl = "https://recent.test/api/v2/recent-messages",
        )

        // The client deliberately has a real request timeout. Keep MockEngine execution off the
        // runTest virtual-time dispatcher so the timeout cannot win before MockEngine responds.
        val result = withContext(Dispatchers.Default) {
            client.load(channel)
        }

        assertEquals(listOf("message-1"), result.messages.map { it.id })
        assertEquals("channel-id", result.messages.single().channelId)
        assertEquals("channel", result.messages.single().channelLogin)
    }

    @Test
    fun parserKeepsRecoveryCodeAndAppliesModerationRows() {
        val raw = "@display-name=Viewer;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :hello"
        val deleted = "@room-id=1234;target-msg-id=message-1;tmi-sent-ts=1720000001000 " +
            ":tmi.twitch.tv CLEARMSG #channel :hello"
        val body = buildJsonObject {
            put("error_code", JsonPrimitive("channel_not_joined"))
            put("messages", buildJsonArray {
                add(JsonPrimitive(raw))
                add(JsonPrimitive(deleted))
            })
        }.toString()

        val result = TwitchRecentMessagesParser.parse(body, channel, limit = 100)

        assertEquals("channel_not_joined", result.errorCode)
        assertTrue(result.messages.single().isDeleted)
    }

    @Test
    fun baseUrlMustUseHttps() {
        assertFailsWith<IllegalArgumentException> {
            TwitchRecentMessagesClient(
                client = HttpClient(MockEngine { respondJson("{\"messages\":[]}") }),
                baseUrl = "http://recent.test/api/v2/recent-messages",
            )
        }
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
