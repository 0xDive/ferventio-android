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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchChatManagementClientTest {
    @Test
    fun getChatSettingsUsesReadContractWithoutModeratorParameter() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { captured ->
            request = captured
            respond(
                ByteReadChannel(
                    """{"data":[{"broadcaster_id":"channel-id","slow_mode":true,"slow_mode_wait_time":12,"follower_mode":true,"follower_mode_duration":30,"subscriber_mode":false,"emote_mode":true,"unique_chat_mode":false}]}""",
                ),
                HttpStatusCode.OK,
            )
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })

        val settings = client.getChatSettings(authentication(), " channel-id ")

        assertEquals("channel-id", settings.channelId)
        assertTrue(settings.slowMode)
        assertEquals(12, settings.slowModeWaitSeconds)
        assertTrue(settings.followerMode)
        assertEquals(30, settings.followerModeDurationMinutes)
        assertTrue(settings.emoteMode)
        val captured = requireNotNull(request)
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/helix/chat/settings", captured.url.encodedPath)
        assertEquals("channel-id", captured.url.parameters["broadcaster_id"])
        assertNull(captured.url.parameters["moderator_id"])
        assertEquals("Bearer access-token", captured.headers[HttpHeaders.Authorization])
    }

    @Test
    fun updateChatSettingsRequiresManageScopeAndSendsOnlyProvidedFields() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { captured ->
            request = captured
            respond(
                ByteReadChannel(
                    """{"data":[{"broadcaster_id":"channel-id","slow_mode":true,"slow_mode_wait_time":15,"follower_mode":false,"subscriber_mode":true,"emote_mode":false,"unique_chat_mode":false}]}""",
                ),
                HttpStatusCode.OK,
            )
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })

        val settings = client.updateChatSettings(
            authentication = authentication(),
            broadcasterId = "channel-id",
            slowMode = true,
            slowModeWaitSeconds = 15,
            subscriberMode = true,
        )

        assertTrue(settings.slowMode)
        assertEquals(15, settings.slowModeWaitSeconds)
        assertTrue(settings.subscriberMode)
        val captured = requireNotNull(request)
        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("moderator-id", captured.url.parameters["moderator_id"])
        val body = requestBody(captured)
        assertTrue(body.getValue("slow_mode").jsonPrimitive.boolean)
        assertEquals(15, body.getValue("slow_mode_wait_time").jsonPrimitive.int)
        assertTrue(body.getValue("subscriber_mode").jsonPrimitive.boolean)
        assertFalse("follower_mode" in body)
    }

    @Test
    fun chattersUseModeratorScopeAndPreserveTotal() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { captured ->
            request = captured
            respond(
                ByteReadChannel(
                    """{"total":42,"data":[{"user_id":"one","user_login":"first","user_name":"First"},{"user_id":"two","user_login":"second","user_name":"Second"}],"pagination":{}}""",
                ),
                HttpStatusCode.OK,
            )
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })

        val result = client.getChatters(authentication(), "channel-id", first = 100)

        assertEquals(42, result.total)
        assertEquals(listOf("one", "two"), result.users.map { it.id })
        val captured = requireNotNull(request)
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/helix/chat/chatters", captured.url.encodedPath)
        assertEquals("channel-id", captured.url.parameters["broadcaster_id"])
        assertEquals("moderator-id", captured.url.parameters["moderator_id"])
        assertEquals("100", captured.url.parameters["first"])
    }

    @Test
    fun pinAndUnpinUseOfficialHelixContract() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { captured ->
            requests += captured
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })

        client.pinChatMessage(
            authentication = authentication(),
            broadcasterId = "channel-id",
            messageId = "message-id",
            durationSeconds = 300,
        )
        client.unpinChatMessage(
            authentication = authentication(),
            broadcasterId = "channel-id",
            messageId = "message-id",
        )

        assertEquals(HttpMethod.Put, requests[0].method)
        assertEquals("/helix/chat/pins", requests[0].url.encodedPath)
        assertEquals("channel-id", requests[0].url.parameters["broadcaster_id"])
        assertEquals("moderator-id", requests[0].url.parameters["moderator_id"])
        assertEquals("message-id", requests[0].url.parameters["message_id"])
        assertEquals("300", requests[0].url.parameters["duration_seconds"])
        assertEquals(HttpMethod.Delete, requests[1].method)
    }

    @Test
    fun missingScopesFailBeforeNetwork() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })
        val auth = authentication(scopes = setOf("user:write:chat"))

        val manage = assertFailsWith<TwitchChatManagementScopeException> {
            client.updateChatSettings(auth, "channel-id", slowMode = true)
        }
        assertEquals("moderator:manage:chat_settings", manage.requiredScope)

        val chatters = assertFailsWith<TwitchChatManagementScopeException> {
            client.getChatters(auth, "channel-id")
        }
        assertEquals("moderator:read:chatters", chatters.requiredScope)
        assertEquals(0, requests)
    }

    @Test
    fun helixFailurePreservesStatusAndMessage() = runTest {
        val engine = MockEngine {
            respond(
                ByteReadChannel(
                    """{"error":"Forbidden","status":403,"message":"user is not a moderator"}""",
                ),
                HttpStatusCode.Forbidden,
            )
        }
        val client = TwitchChatManagementClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchChatManagementException> {
            client.getChatters(authentication(), "channel-id")
        }

        assertEquals(403, error.statusCode)
        assertEquals("user is not a moderator", error.twitchMessage)
        assertTrue(error.message.orEmpty().contains("HTTP 403"))
    }

    private suspend fun requestBody(request: HttpRequestData) = Json
        .parseToJsonElement(request.body.toByteArray().decodeToString())
        .jsonObject

    private fun authentication(
        scopes: Set<String> = setOf(
            "moderator:manage:chat_settings",
            "moderator:manage:chat_messages",
            "moderator:read:chatters",
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
