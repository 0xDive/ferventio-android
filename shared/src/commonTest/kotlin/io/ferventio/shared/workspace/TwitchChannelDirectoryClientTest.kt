package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwitchChannelDirectoryClientTest {
    @Test
    fun resolvesSavedLoginsWithLeaseHeadersAndRepeatedLoginQuery() = runTest {
        var authorization: String? = null
        var clientId: String? = null
        var requestedLogins: List<String> = emptyList()
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            clientId = request.headers["Client-Id"]
            requestedLogins = request.url.parameters.getAll("login").orEmpty()
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {
                          "id": "2",
                          "login": "beta",
                          "display_name": "Beta",
                          "profile_image_url": "https://example.test/beta.png"
                        },
                        {
                          "id": "1",
                          "login": "alpha",
                          "display_name": "Alpha",
                          "profile_image_url": "https://example.test/alpha.png"
                        },
                        {
                          "id": "unexpected",
                          "login": "unexpected",
                          "display_name": "Unexpected"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = TwitchChannelDirectoryClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val channels = client.resolveByLogins(
            authentication = authentication(),
            logins = listOf(" Alpha ", "beta", "ALPHA"),
        )

        assertEquals(listOf("alpha", "beta"), requestedLogins)
        assertEquals("Bearer access-token", authorization)
        assertEquals("client-id", clientId)
        assertEquals(listOf("2", "1"), channels.map { it.id })
        assertTrue(channels.none { it.id == "unexpected" })
    }

    @Test
    fun resolvesModeratedChannelsWithAndroidHelixContract() = runTest {
        var authorization: String? = null
        var clientId: String? = null
        var requestedUserId: String? = null
        var requestedFirst: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            clientId = request.headers["Client-Id"]
            requestedUserId = request.url.parameters["user_id"]
            requestedFirst = request.url.parameters["first"]
            assertEquals("/helix/moderation/channels", request.url.encodedPath)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {"broadcaster_id":"2"},
                        {"broadcaster_id":"3"},
                        {"broadcaster_id":"2"},
                        {"broadcaster_id":" "}
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = TwitchChannelDirectoryClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val moderated = client.resolveModeratedChannelIds(authentication())

        assertEquals("Bearer access-token", authorization)
        assertEquals("client-id", clientId)
        assertEquals("viewer-id", requestedUserId)
        assertEquals("100", requestedFirst)
        assertEquals(setOf("2", "3"), moderated)
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
