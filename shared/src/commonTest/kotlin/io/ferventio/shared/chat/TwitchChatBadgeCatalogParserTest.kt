package io.ferventio.shared.chat

import io.ferventio.app.domain.chatBadgeAssetKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TwitchChatBadgeCatalogParserTest {
    @Test
    fun parsesRelayCompatibleHelixEnvelope() {
        val assets = TwitchChatBadgeCatalogParser.parse(BADGE_PAYLOAD)

        val asset = assets.getValue(chatBadgeAssetKey("subscriber", "12"))
        assertEquals("subscriber", asset.setId)
        assertEquals("12", asset.id)
        assertEquals("https://cdn.example/sub-1.png", asset.imageUrl1x)
        assertEquals("https://cdn.example/sub-2.png", asset.imageUrl2x)
        assertEquals("https://cdn.example/sub-2.png", asset.imageUrl4x)
        assertEquals("Subscriber", asset.title)
        assertEquals("Subscriber badge", asset.description)
    }

    @Test
    fun ignoresIncompleteBadgeVersions() {
        val assets = TwitchChatBadgeCatalogParser.parse(
            """
            {
              "data": [
                {
                  "set_id": "vip",
                  "versions": [
                    { "id": "1" },
                    { "id": "2", "image_url_1x": "https://cdn.example/vip.png" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, assets.size)
        assertTrue(chatBadgeAssetKey("vip", "2") in assets)
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<IllegalStateException> {
            TwitchChatBadgeCatalogParser.parse("not-json")
        }
    }

    @Test
    fun metadataRelayUrlValidationMatchesBackendAuthSafetyRules() {
        assertEquals(
            "https://ferventio.example/api",
            normalizeFerventioMetadataServerUrl("  https://ferventio.example/api///  "),
        )
        assertFailsWith<IllegalArgumentException> {
            normalizeFerventioMetadataServerUrl("http://ferventio.example")
        }
        assertFailsWith<IllegalArgumentException> {
            normalizeFerventioMetadataServerUrl("https://user:pass@ferventio.example")
        }
        assertFailsWith<IllegalArgumentException> {
            normalizeFerventioMetadataServerUrl("https://ferventio.example?redirect=evil")
        }
        assertFailsWith<IllegalArgumentException> {
            normalizeFerventioMetadataServerUrl("https://ferventio.example/#fragment")
        }
    }

    @Test
    fun broadcasterIdsAreStrictlyNumeric() {
        assertEquals("12345", requireTwitchBroadcasterId(" 12345 "))
        assertFailsWith<IllegalArgumentException> { requireTwitchBroadcasterId("") }
        assertFailsWith<IllegalArgumentException> { requireTwitchBroadcasterId("abc") }
        assertFailsWith<IllegalArgumentException> { requireTwitchBroadcasterId("12/34") }
        assertFailsWith<IllegalArgumentException> { requireTwitchBroadcasterId("1".repeat(33)) }
    }

    @Test
    fun relayClientUsesPublicGlobalAndChannelRoutes() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            respond(
                content = BADGE_PAYLOAD,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        val httpClient = HttpClient(engine)
        try {
            val client = FerventioBadgeRelayClient(httpClient)
            client.loadGlobal("https://ferventio.example/")
            client.loadChannel("https://ferventio.example", "12345")

            assertEquals(
                listOf(
                    "https://ferventio.example/v1/twitch/badges/global",
                    "https://ferventio.example/v1/twitch/badges/12345",
                ),
                requestedUrls,
            )
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun relayClientSurfacesBackendStatusWithoutLeakingLargeBodies() = runTest {
        val engine = MockEngine {
            respond(
                content = "x".repeat(1_000),
                status = HttpStatusCode.BadGateway,
            )
        }
        val httpClient = HttpClient(engine)
        try {
            val error = assertFailsWith<FerventioBadgeRelayException> {
                FerventioBadgeRelayClient(httpClient).loadGlobal("https://ferventio.example")
            }
            assertEquals(502, error.statusCode)
            assertEquals(300, error.responseBody.length)
        } finally {
            httpClient.close()
        }
    }

    private companion object {
        val BADGE_PAYLOAD =
            """
            {
              "data": [
                {
                  "set_id": "subscriber",
                  "versions": [
                    {
                      "id": "12",
                      "image_url_1x": "https://cdn.example/sub-1.png",
                      "image_url_2x": "https://cdn.example/sub-2.png",
                      "title": "Subscriber",
                      "description": "Subscriber badge"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
    }
}
