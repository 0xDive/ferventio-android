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
import kotlin.test.assertNull

class TwitchCheermoteClientTest {
    @Test
    fun loadsChannelCheermotesWithHelixHeadersAndTierImages() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/helix/bits/cheermotes", request.url.encodedPath)
            assertEquals("channel-1", request.url.parameters["broadcaster_id"])
            assertEquals("Bearer twitch-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client", request.headers["Client-Id"])
            respondJson(CHEERMOTES_JSON)
        }
        val client = TwitchCheermoteClient(HttpClient(engine) { expectSuccess = false })

        val assets = client.load(authentication(), " channel-1 ")

        assertEquals(listOf(1, 100), assets.getValue("cheer").map { it.minBits })
        assertEquals("https://cdn.test/cheer-1-animated.gif", assets["cheer"]?.first()?.animatedImageUrl)
        assertEquals("https://cdn.test/cheer-100-static.png", assets["cheer"]?.last()?.staticImageUrl)
        assertEquals(listOf(1), assets.getValue("custom").map { it.minBits })
    }

    @Test
    fun blankBroadcasterLoadsGlobalCatalogWithoutQueryParameter() = runTest {
        val engine = MockEngine { request ->
            assertNull(request.url.parameters["broadcaster_id"])
            respondJson(CHEERMOTES_JSON)
        }
        val client = TwitchCheermoteClient(HttpClient(engine) { expectSuccess = false })

        client.load(authentication(), "   ")
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
        val CHEERMOTES_JSON = """
            {
              "data": [
                {
                  "prefix": "Cheer",
                  "tiers": [
                    {
                      "id": "100",
                      "min_bits": 100,
                      "color": "#9c3ee8",
                      "can_cheer": true,
                      "images": {
                        "dark": {
                          "animated": {"1": "https://cdn.test/cheer-100-animated-1.gif", "2": "https://cdn.test/cheer-100-animated.gif"},
                          "static": {"2": "https://cdn.test/cheer-100-static.png"}
                        }
                      }
                    },
                    {
                      "id": "1",
                      "min_bits": 1,
                      "color": "#979797",
                      "can_cheer": true,
                      "images": {
                        "dark": {
                          "animated": {"2": "https://cdn.test/cheer-1-animated.gif"},
                          "static": {"2": "https://cdn.test/cheer-1-static.png"}
                        }
                      }
                    }
                  ]
                },
                {
                  "prefix": "Custom",
                  "tiers": [
                    {
                      "id": 1,
                      "min_bits": 1,
                      "color": "#ffffff",
                      "can_cheer": true,
                      "images": {
                        "dark": {
                          "static": {"1.5": "https://cdn.test/custom-static.png"}
                        }
                      }
                    },
                    {
                      "id": 100,
                      "min_bits": 100,
                      "color": "#ffffff",
                      "can_cheer": false,
                      "images": {
                        "dark": {
                          "static": {"2": "https://cdn.test/custom-disabled.png"}
                        }
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
