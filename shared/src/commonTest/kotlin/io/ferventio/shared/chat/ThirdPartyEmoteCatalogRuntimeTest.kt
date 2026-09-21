package io.ferventio.shared.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThirdPartyEmoteCatalogRuntimeTest {
    @Test
    fun repeatedChannelLoadReusesMergedCatalogWithinTtl() = runTest {
        var requests = 0
        val runtime = runtime { requests += 1 }

        runtime.load("channel-1")
        runtime.load("channel-1")

        assertEquals(6, requests)
    }

    @Test
    fun differentChannelsShareGlobalProviderRequests() = runTest {
        var requests = 0
        val runtime = runtime { requests += 1 }

        runtime.load("channel-1")
        runtime.load("channel-2")

        assertEquals(9, requests)
    }

    @Test
    fun expiredChannelAndGlobalCachesRefresh() = runTest {
        var now = 1_000L
        var requests = 0
        val runtime = runtime(
            ttlMillis = 500L,
            nowEpochMillis = { now },
            onRequest = { requests += 1 },
        )

        runtime.load("channel-1")
        now += 501L
        runtime.load("channel-1")

        assertEquals(12, requests)
    }

    private fun runtime(
        ttlMillis: Long = 60_000L,
        nowEpochMillis: () -> Long = { 1_000L },
        onRequest: () -> Unit,
    ): ThirdPartyEmoteCatalogRuntime {
        val engine = MockEngine { request ->
            onRequest()
            val response = when {
                request.url.host == "7tv.io" &&
                    request.url.encodedPath == "/v3/emote-sets/global" ->
                    "{}"
                request.url.host == "api.frankerfacez.com" &&
                    request.url.encodedPath == "/v1/set/global" ->
                    """{"default_sets":[],"sets":{}}"""
                request.url.host == "api.betterttv.net" &&
                    request.url.encodedPath == "/3/cached/emotes/global" ->
                    "[]"
                request.url.host == "7tv.io" ->
                    """{"emote_set":{"emotes":[]}}"""
                request.url.host == "api.frankerfacez.com" ->
                    """{"room":{},"sets":{}}"""
                request.url.host == "api.betterttv.net" ->
                    """{"sharedEmotes":[],"channelEmotes":[]}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(ByteReadChannel(response), HttpStatusCode.OK)
        }
        return ThirdPartyEmoteCatalogRuntime(
            client = ThirdPartyEmoteCatalogClient(
                HttpClient(engine) { expectSuccess = false },
            ),
            ttlMillis = ttlMillis,
            nowEpochMillis = nowEpochMillis,
        )
    }
}
