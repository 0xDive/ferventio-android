package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TwitchChatBadgeClientTest {
    @Test
    fun globalBadgesUseHelixHeadersAndParseVersions() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/helix/chat/badges/global", request.url.encodedPath)
            assertEquals("Bearer twitch-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client", request.headers["Client-Id"])
            respondJson(BADGES_JSON)
        }
        val client = TwitchChatBadgeClient(HttpClient(engine) { expectSuccess = false })

        val assets = client.loadGlobal(authentication())

        assertEquals("Moderator", assets["moderator/1"]?.title)
        assertEquals("https://cdn.test/mod-2x.png", assets["moderator/1"]?.imageUrl2x)
    }

    @Test
    fun channelBadgesSendBroadcasterId() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/helix/chat/badges", request.url.encodedPath)
            assertEquals("channel-1", request.url.parameters["broadcaster_id"])
            respondJson(BADGES_JSON)
        }
        val client = TwitchChatBadgeClient(HttpClient(engine) { expectSuccess = false })

        client.loadChannel(authentication(), " channel-1 ")
    }

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-token",
            expiresAtEpochMillis = 9_000_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "twitch-token",
            leaseExpiresAtEpochMillis = 2_000_000L,
            twitchExpiresAtEpochMillis = 8_000_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 9_000_000L,
            session = TwitchSession(
                clientId = "client",
                userId = "user",
                login = "login",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_000L,
            ),
        ),
    )

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        val BADGES_JSON = """
            {
              "data": [
                {
                  "set_id": "moderator",
                  "versions": [
                    {
                      "id": "1",
                      "image_url_1x": "https://cdn.test/mod-1x.png",
                      "image_url_2x": "https://cdn.test/mod-2x.png",
                      "image_url_4x": "https://cdn.test/mod-4x.png",
                      "title": "Moderator",
                      "description": "Moderator badge"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
