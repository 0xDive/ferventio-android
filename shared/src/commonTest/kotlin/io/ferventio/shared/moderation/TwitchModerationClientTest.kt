package io.ferventio.shared.moderation

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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchModerationClientTest {
    @Test
    fun banAndTimeoutUseAuthenticatedHelixContract() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured += request
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        client.banUser(
            authentication = authentication(),
            broadcasterId = " channel-id ",
            targetUserId = " target-id ",
            reason = " repeated spam ",
        )
        client.timeoutUser(
            authentication = authentication(),
            broadcasterId = "channel-id",
            targetUserId = "other-target",
            durationSeconds = 600,
        )

        val ban = captured[0]
        assertEquals(HttpMethod.Post, ban.method)
        assertEquals("/helix/moderation/bans", ban.url.encodedPath)
        assertEquals("channel-id", ban.url.parameters["broadcaster_id"])
        assertEquals("moderator-id", ban.url.parameters["moderator_id"])
        assertEquals("Bearer access-token", ban.headers[HttpHeaders.Authorization])
        assertEquals("client-id", ban.headers["Client-Id"])
        val banData = requestBody(ban).getValue("data").jsonObject
        assertEquals("target-id", banData.getValue("user_id").jsonPrimitive.content)
        assertEquals("repeated spam", banData.getValue("reason").jsonPrimitive.content)
        assertFalse("duration" in banData)

        val timeoutData = requestBody(captured[1]).getValue("data").jsonObject
        assertEquals("other-target", timeoutData.getValue("user_id").jsonPrimitive.content)
        assertEquals(600, timeoutData.getValue("duration").jsonPrimitive.int)
        assertFalse("reason" in timeoutData)
    }

    @Test
    fun unbanRoutesTargetThroughQueryParameters() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        client.unbanUser(
            authentication = authentication(),
            broadcasterId = "channel-id",
            targetUserId = "target-id",
        )

        val request = requireNotNull(captured)
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/helix/moderation/bans", request.url.encodedPath)
        assertEquals("channel-id", request.url.parameters["broadcaster_id"])
        assertEquals("moderator-id", request.url.parameters["moderator_id"])
        assertEquals("target-id", request.url.parameters["user_id"])
    }

    @Test
    fun chatDeletionDistinguishesSingleMessageFromClear() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured += request
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        client.deleteChatMessage(
            authentication = authentication(),
            broadcasterId = "channel-id",
            messageId = " message-id ",
        )
        client.clearChatMessages(
            authentication = authentication(),
            broadcasterId = "channel-id",
        )

        val single = captured[0]
        assertEquals(HttpMethod.Delete, single.method)
        assertEquals("/helix/moderation/chat", single.url.encodedPath)
        assertEquals("channel-id", single.url.parameters["broadcaster_id"])
        assertEquals("moderator-id", single.url.parameters["moderator_id"])
        assertEquals("message-id", single.url.parameters["message_id"])

        val clear = captured[1]
        assertEquals(HttpMethod.Delete, clear.method)
        assertEquals("/helix/moderation/chat", clear.url.encodedPath)
        assertNull(clear.url.parameters["message_id"])
    }

    @Test
    fun missingMutationScopeFailsBeforeNetworkRequest() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        val bannedUsersError = assertFailsWith<TwitchModerationScopeException> {
            client.banUser(
                authentication = authentication(scopes = setOf("chat:read")),
                broadcasterId = "channel-id",
                targetUserId = "target-id",
            )
        }
        assertEquals("moderator:manage:banned_users", bannedUsersError.requiredScope)

        val chatMessagesError = assertFailsWith<TwitchModerationScopeException> {
            client.deleteChatMessage(
                authentication = authentication(scopes = setOf("chat:read")),
                broadcasterId = "channel-id",
                messageId = "message-id",
            )
        }
        assertEquals("moderator:manage:chat_messages", chatMessagesError.requiredScope)
        assertEquals(0, requestCount)
    }

    @Test
    fun helixFailurePreservesStatusAndTwitchMessage() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"Forbidden","status":403,"message":"user is not a moderator"}""",
                ),
                status = HttpStatusCode.Forbidden,
            )
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchModerationMutationException> {
            client.timeoutUser(
                authentication = authentication(),
                broadcasterId = "channel-id",
                targetUserId = "target-id",
                durationSeconds = 60,
            )
        }

        assertEquals("timeout", error.operation)
        assertEquals(403, error.statusCode)
        assertEquals("user is not a moderator", error.twitchMessage)
        assertTrue(error.message.orEmpty().contains("HTTP 403"))
    }

    @Test
    fun unsafeTargetsAndInvalidTimeoutsAreRejectedLocally() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        assertFailsWith<IllegalArgumentException> {
            client.banUser(authentication(), "channel-id", "channel-id")
        }
        assertFailsWith<IllegalArgumentException> {
            client.banUser(authentication(), "channel-id", "moderator-id")
        }
        assertFailsWith<IllegalArgumentException> {
            client.timeoutUser(authentication(), "channel-id", "target-id", durationSeconds = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            client.timeoutUser(authentication(), "channel-id", "target-id", durationSeconds = 1_209_601)
        }
        assertFailsWith<IllegalArgumentException> {
            client.banUser(authentication(), "channel-id", "target-id", reason = "x".repeat(501))
        }
        assertEquals(0, requestCount)
    }

    private fun requestBody(request: HttpRequestData) = Json
        .parseToJsonElement(request.body.toByteArray().decodeToString())
        .jsonObject

    private fun authentication(
        scopes: Set<String> = setOf(
            "moderator:manage:banned_users",
            "moderator:manage:chat_messages",
        ),
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
                userId = "moderator-id",
                login = "moderator",
                scopes = scopes,
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
