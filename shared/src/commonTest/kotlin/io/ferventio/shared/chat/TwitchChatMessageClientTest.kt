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
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchChatMessageClientTest {
    @Test
    fun sendUsesAuthenticatedHelixContractAndReplyId() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel(
                    """{"data":[{"message_id":"server-message","is_sent":true}]}""",
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = TwitchChatMessageClient(HttpClient(engine) { expectSuccess = false })

        val result = client.sendMessage(
            authentication = authentication(),
            broadcasterId = " channel-id ",
            message = " hello chat ",
            replyParentMessageId = " parent-id ",
        )

        assertEquals("server-message", result.messageId)
        val request = requireNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/helix/chat/messages", request.url.encodedPath)
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("client-id", request.headers["Client-Id"])
        val body = requestBody(request)
        assertEquals("channel-id", body.getValue("broadcaster_id").jsonPrimitive.content)
        assertEquals("sender-id", body.getValue("sender_id").jsonPrimitive.content)
        assertEquals("hello chat", body.getValue("message").jsonPrimitive.content)
        assertEquals("parent-id", body.getValue("reply_parent_message_id").jsonPrimitive.content)
    }

    @Test
    fun omittedReplyIsNotSerialized() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                ByteReadChannel("""{"data":[{"message_id":"server-message","is_sent":true}]}"""),
                HttpStatusCode.OK,
            )
        }
        val client = TwitchChatMessageClient(HttpClient(engine) { expectSuccess = false })

        client.sendMessage(authentication(), "channel-id", "hello")

        assertNull(requestBody(requireNotNull(captured))["reply_parent_message_id"])
    }

    @Test
    fun missingWriteScopeFailsBeforeNetworkRequest() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchChatMessageClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchChatMessageScopeException> {
            client.sendMessage(
                authentication = authentication(scopes = setOf("user:read:chat")),
                broadcasterId = "channel-id",
                message = "hello",
            )
        }

        assertEquals("user:write:chat", error.requiredScope)
        assertEquals(0, requests)
    }

    @Test
    fun droppedMessagePreservesTwitchReason() = runTest {
        val engine = MockEngine {
            respond(
                ByteReadChannel(
                    """{"data":[{"message_id":"server-message","is_sent":false,"drop_reason":{"code":"followers_only","message":"Followers-only mode"}}]}""",
                ),
                HttpStatusCode.OK,
            )
        }
        val client = TwitchChatMessageClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchChatMessageDroppedException> {
            client.sendMessage(authentication(), "channel-id", "hello")
        }

        assertEquals("followers_only", error.code)
        assertEquals("Followers-only mode", error.twitchMessage)
    }

    @Test
    fun rateLimitPreservesRetryAtMillis() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"message":"Too Many Requests"}"""),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf("Retry-After", "7"),
            )
        }
        val client = TwitchChatMessageClient(
            client = HttpClient(engine) { expectSuccess = false },
            currentEpochMillis = { 10_000L },
        )

        val error = assertFailsWith<TwitchChatMessageMutationException> {
            client.sendMessage(authentication(), "channel-id", "hello")
        }

        assertEquals(429, error.statusCode)
        assertEquals(17_000L, error.retryAtMillis)
        assertTrue(error.message.orEmpty().contains("rate limit"))
    }

    @Test
    fun oversizedMessageIsRejectedLocally() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchChatMessageClient(HttpClient(engine) { expectSuccess = false })

        assertFailsWith<IllegalArgumentException> {
            client.sendMessage(authentication(), "channel-id", "x".repeat(501))
        }
        assertEquals(0, requests)
    }

    private suspend fun requestBody(request: HttpRequestData) = Json
        .parseToJsonElement(request.body.toByteArray().decodeToString())
        .jsonObject

    private fun authentication(
        scopes: Set<String> = setOf("user:write:chat"),
    ) = StoredAuthentication(
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
                userId = "sender-id",
                login = "sender",
                scopes = scopes,
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
