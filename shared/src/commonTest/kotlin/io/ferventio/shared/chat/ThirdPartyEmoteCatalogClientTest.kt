package io.ferventio.shared.chat

import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdPartyEmoteCatalogClientTest {
    @Test
    fun sevenTvBit256IsZeroWidth() = runTest {
        val engine = MockEngine { request ->
            val body = when (request.url.host) {
                "7tv.io" -> """{"emotes":[{"id":"z","name":"Z","flags":256,"data":{"id":"z","name":"Z","host":{"url":"//cdn.test/z","files":[{"name":"2x.webp"}]}}}]}"""
                "api.frankerfacez.com" -> """{"default_sets":[],"sets":{}}"""
                "api.betterttv.net" -> "[]"
                else -> error("Unexpected host")
            }
            respond(ByteReadChannel(body), HttpStatusCode.OK)
        }
        val client = ThirdPartyEmoteCatalogClient(HttpClient(engine) { expectSuccess = false })

        val globals = client.loadGlobals()

        assertTrue(globals.provider("7tv").getValue("Z").zeroWidth)
    }

    @Test
    fun mergeUsesSevenTvThenFfzThenBttvPrecedence() {
        val client = ThirdPartyEmoteCatalogClient(
            HttpClient(MockEngine { error("network not expected") }),
        )
        val merged = client.mergeForChannel(
            ThirdPartyProviderCatalogs(
                mapOf(
                    "7tv" to mapOf("Same" to asset("7tv")),
                    "frankerfacez" to mapOf("Same" to asset("frankerfacez")),
                    "betterttv" to mapOf("Same" to asset("betterttv")),
                ),
            ),
            ThirdPartyProviderCatalogs(),
        )

        assertEquals("betterttv", merged.getValue("Same").provider)
    }

    private fun asset(provider: String) = ThirdPartyEmoteAsset(
        id = provider,
        code = "Same",
        provider = provider,
        imageType = "png",
        animated = false,
        imageUrl1x = "https://cdn.test/$provider.png",
        imageUrl2x = "https://cdn.test/$provider.png",
        imageUrl3x = "https://cdn.test/$provider.png",
        scope = EmoteScope.GLOBAL,
    )
}
