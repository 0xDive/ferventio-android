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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwitchModerationWarnParityTest {
    @Test
    fun warningUsesAuthenticatedHelixContract() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        client.warnUser(
            authentication = authentication(),
            broadcasterId = " channel-id ",
            targetUserId = " target-id ",
            reason = " repeated spoilers ",
        )

        val request = requireNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/helix/moderation/warnings", request.url.encodedPath)
        assertEquals("channel-id", request.url.parameters["broadcaster_id"])
        assertEquals("moderator-id", request.url.parameters["moderator_id"])
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("client-id", request.headers["Client-Id"])
        val data = requestBody(request).getValue("data").jsonObject
        assertEquals("target-id", data.getValue("user_id").jsonPrimitive.content)
        assertEquals("repeated spoilers", data.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun missingWarningScopeFailsBeforeNetworkRequest() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount += 1
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }
        val client = TwitchModerationClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchModerationScopeException> {
            client.warnUser(
                authentication = authentication(scopes = setOf("moderator:manage:banned_users")),
                broadcasterId = "channel-id",
                targetUserId = "target-id",
                reason = "reason",
            )
        }

        assertEquals("moderator:manage:warnings", error.requiredScope)
        assertEquals(0, requestCount)
    }

    private suspend fun requestBody(request: HttpRequestData) = Json
        .parseToJsonElement(request.body.toByteArray().decodeToString())
        .jsonObject

    private fun authentication(
        scopes: Set<String> = setOf(
            "moderator:manage:banned_users",
            "moderator:manage:warnings",
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
