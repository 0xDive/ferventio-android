package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchEventSubSubscriptionClientTest {
    @Test
    fun createsWebsocketSubscriptionWithAndroidWireContract() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.Accepted,
            )
        }
        val client = TwitchEventSubSubscriptionClient(
            HttpClient(engine) { expectSuccess = false },
        )

        client.createSubscription(
            authentication = authentication(),
            sessionId = "socket-session",
            broadcasterId = "channel-id",
            type = "channel.chat.message",
        )

        val request = requireNotNull(captured)
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("client-id", request.headers["Client-Id"])
        assertTrue(request.body.contentType?.toString()?.startsWith("application/json") == true)
        val root = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
        assertEquals("channel.chat.message", root.getValue("type").jsonPrimitive.content)
        assertEquals("1", root.getValue("version").jsonPrimitive.content)
        val condition = root.getValue("condition").jsonObject
        assertEquals("channel-id", condition.getValue("broadcaster_user_id").jsonPrimitive.content)
        assertEquals("viewer-id", condition.getValue("user_id").jsonPrimitive.content)
        val transport = root.getValue("transport").jsonObject
        assertEquals("websocket", transport.getValue("method").jsonPrimitive.content)
        assertEquals("socket-session", transport.getValue("session_id").jsonPrimitive.content)
    }

    @Test
    fun listsEnabledWebsocketSubscriptionsAcrossPages() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            val after = request.url.parameters["after"]
            val body = if (after == null) {
                """
                {
                  "data": [
                    {
                      "id": "sub-1",
                      "status": "enabled",
                      "type": "channel.chat.message",
                      "version": "1",
                      "transport": {
                        "method": "websocket",
                        "session_id": "session-a",
                        "connected_at": "2026-09-20T05:00:00Z"
                      }
                    }
                  ],
                  "pagination": {"cursor": "next-page"}
                }
                """.trimIndent()
            } else {
                """
                {
                  "data": [
                    {
                      "id": "sub-2",
                      "status": "enabled",
                      "type": "channel.chat.notification",
                      "version": "1",
                      "transport": {
                        "method": "websocket",
                        "session_id": "session-b",
                        "connected_at": "2026-09-20T05:01:00Z"
                      }
                    }
                  ],
                  "pagination": {}
                }
                """.trimIndent()
            }
            respond(ByteReadChannel(body), HttpStatusCode.OK)
        }
        val client = TwitchEventSubSubscriptionClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val subscriptions = client.listEnabledWebSocketSubscriptions(authentication())

        assertEquals(listOf("sub-1", "sub-2"), subscriptions.map { it.id })
        assertEquals(2, requests.size)
        assertEquals(HttpMethod.Get, requests[0].method)
        assertEquals("enabled", requests[0].url.parameters["status"])
        assertEquals(null, requests[0].url.parameters["first"])
        assertEquals("next-page", requests[1].url.parameters["after"])
        requests.forEach { request ->
            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client-id", request.headers["Client-Id"])
        }
    }

    @Test
    fun deleteSubscriptionIsIdempotentForAlreadyMissingSubscription() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("sub-1", request.url.parameters["id"])
            respond(
                ByteReadChannel(""),
                if (requestCount == 1) HttpStatusCode.NoContent else HttpStatusCode.NotFound,
            )
        }
        val client = TwitchEventSubSubscriptionClient(
            HttpClient(engine) { expectSuccess = false },
        )

        assertTrue(client.deleteSubscription(authentication(), " sub-1 "))
        assertFalse(client.deleteSubscription(authentication(), "sub-1"))
    }

    @Test
    fun supportsBroadcasterOnlyConditions() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(ByteReadChannel("{}"), HttpStatusCode.Accepted)
        }
        val client = TwitchEventSubSubscriptionClient(
            HttpClient(engine) { expectSuccess = false },
        )

        client.createSubscription(
            authentication = authentication(),
            sessionId = "socket-session",
            broadcasterId = "channel-id",
            type = "channel.chat.clear",
            identityConditionKey = null,
        )

        val request = requireNotNull(captured)
        val root = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
        val condition = root.getValue("condition").jsonObject
        assertEquals("channel-id", condition.getValue("broadcaster_user_id").jsonPrimitive.content)
        assertFalse("user_id" in condition)
    }

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "access-token",
            leaseExpiresAtEpochMillis = 1_300_000L,
            twitchExpiresAtEpochMillis = 8_200_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 4_600_000L,
            session = TwitchSession(
                clientId = "client-id",
                userId = "viewer-id",
                login = "viewer",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
