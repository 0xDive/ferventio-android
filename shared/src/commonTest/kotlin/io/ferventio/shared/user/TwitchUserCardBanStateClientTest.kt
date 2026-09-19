package io.ferventio.shared.user

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchUserCardBanStateClientTest {
    @Test
    fun permanentBanLookupDistinguishesBanTimeoutAndMissingTarget() = runTest {
        val engine = MockEngine { request ->
            assertEquals("api.twitch.tv", request.url.host)
            assertEquals("/helix/moderation/banned", request.url.encodedPath)
            assertEquals("channel-id", request.url.parameters["broadcaster_id"])
            assertEquals("1", request.url.parameters["first"])
            assertEquals("Bearer twitch-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client", request.headers["Client-Id"])
            val body = when (request.url.parameters["user_id"]) {
                "permanent-user" -> """{"data":[{"user_id":"permanent-user","expires_at":""}]}"""
                "timeout-user" -> """{"data":[{"user_id":"timeout-user","expires_at":"2026-09-16T00:00:00Z"}]}"""
                else -> """{"data":[]}"""
            }
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val client = TwitchUserCardClient(HttpClient(engine) { expectSuccess = false })
        val authentication = authentication()

        assertTrue(
            client.loadPermanentBanState(
                authentication = authentication,
                broadcasterId = " channel-id ",
                targetUserId = " permanent-user ",
            ),
        )
        assertFalse(
            client.loadPermanentBanState(
                authentication = authentication,
                broadcasterId = "channel-id",
                targetUserId = "timeout-user",
            ),
        )
        assertFalse(
            client.loadPermanentBanState(
                authentication = authentication,
                broadcasterId = "channel-id",
                targetUserId = "not-banned",
            ),
        )
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
                userId = "moderator-id",
                login = "moderator",
                scopes = setOf("moderator:manage:banned_users"),
                expiresInSeconds = 7_000L,
            ),
        ),
    )

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )
}
