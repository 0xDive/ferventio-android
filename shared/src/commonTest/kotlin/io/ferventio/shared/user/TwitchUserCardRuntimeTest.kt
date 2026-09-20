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

class TwitchUserCardRuntimeTest {
    @Test
    fun repeatedCardOpenReusesProfileAndRelationshipWithinTtl() = runTest {
        var twitchRequests = 0
        var relationshipRequests = 0
        val runtime = runtime(
            onTwitchRequest = { twitchRequests += 1 },
            onRelationshipRequest = { relationshipRequests += 1 },
        )

        runtime.enrich(authentication(), "user-1", "viewer", "channel-one")
        runtime.enrich(authentication(), "user-1", "viewer", "channel-one")

        assertEquals(1, twitchRequests)
        assertEquals(1, relationshipRequests)
    }

    @Test
    fun sameUserAcrossChannelsReusesProfileButNotRelationship() = runTest {
        var twitchRequests = 0
        var relationshipRequests = 0
        val runtime = runtime(
            onTwitchRequest = { twitchRequests += 1 },
            onRelationshipRequest = { relationshipRequests += 1 },
        )

        runtime.enrich(authentication(), "user-1", "viewer", "channel-one")
        runtime.enrich(authentication(), "user-1", "viewer", "channel-two")

        assertEquals(1, twitchRequests)
        assertEquals(2, relationshipRequests)
    }

    @Test
    fun expiredCachesRefreshProfileAndRelationship() = runTest {
        var now = 1_000L
        var twitchRequests = 0
        var relationshipRequests = 0
        val runtime = runtime(
            ttlMillis = 500L,
            nowEpochMillis = { now },
            onTwitchRequest = { twitchRequests += 1 },
            onRelationshipRequest = { relationshipRequests += 1 },
        )

        runtime.enrich(authentication(), "user-1", "viewer", "channel-one")
        now += 501L
        runtime.enrich(authentication(), "user-1", "viewer", "channel-one")

        assertEquals(2, twitchRequests)
        assertEquals(2, relationshipRequests)
    }

    private fun runtime(
        ttlMillis: Long = 60_000L,
        nowEpochMillis: () -> Long = { 1_000L },
        onTwitchRequest: () -> Unit,
        onRelationshipRequest: () -> Unit,
    ): TwitchUserCardRuntime {
        val engine = MockEngine { request ->
            when (request.url.host) {
                "api.twitch.tv" -> {
                    onTwitchRequest()
                    respond(
                        content = ByteReadChannel(TWITCH_USER_JSON),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                "api.ivr.fi" -> {
                    onRelationshipRequest()
                    respond(
                        content = ByteReadChannel(RELATIONSHIP_JSON),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                else -> error("Unexpected host: ${request.url.host}")
            }
        }
        return TwitchUserCardRuntime(
            client = TwitchUserCardClient(
                HttpClient(engine) { expectSuccess = false },
            ),
            ttlMillis = ttlMillis,
            nowEpochMillis = nowEpochMillis,
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
                userId = "signed-in-user",
                login = "signed_in",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_000L,
            ),
        ),
    )

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private companion object {
        val TWITCH_USER_JSON = """
            {
              "data": [
                {
                  "id": "user-1",
                  "login": "viewer",
                  "display_name": "Viewer",
                  "profile_image_url": "https://cdn.test/avatar.png",
                  "created_at": "2020-01-02T03:04:05Z"
                }
              ]
            }
        """.trimIndent()

        val RELATIONSHIP_JSON = """
            {
              "followedAt": "2024-01-01T00:00:00Z",
              "statusHidden": false,
              "meta": null,
              "cumulative": null
            }
        """.trimIndent()
    }
}
