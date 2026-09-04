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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TwitchUserCardClientTest {
    @Test
    fun loadsTwitchProfileWithAuthenticatedHelixHeaders() = runTest {
        val engine = MockEngine { request ->
            assertEquals("api.twitch.tv", request.url.host)
            assertEquals("/helix/users", request.url.encodedPath)
            assertEquals("user-1", request.url.parameters["id"])
            assertEquals("Bearer twitch-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client", request.headers["Client-Id"])
            respond(
                content = ByteReadChannel(TWITCH_USER_JSON),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val client = TwitchUserCardClient(HttpClient(engine) { expectSuccess = false })

        val user = client.loadUser(authentication(), " user-1 ", "ignored")

        assertEquals("viewer", user.login)
        assertEquals("Viewer", user.displayName)
        assertEquals("https://cdn.test/avatar.png", user.profileImageUrl)
        assertEquals("2020-01-02T03:04:05Z", user.createdAt)
        assertEquals("Profile description", user.description)
    }

    @Test
    fun loadsPublicRelationshipUsingNormalizedLogins() = runTest {
        val engine = MockEngine { request ->
            assertEquals("api.ivr.fi", request.url.host)
            assertEquals("/v2/twitch/subage/viewer/channel_name", request.url.encodedPath)
            assertEquals("Ferventio", request.headers[HttpHeaders.UserAgent])
            respond(
                content = ByteReadChannel(RELATIONSHIP_JSON),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val client = TwitchUserCardClient(HttpClient(engine) { expectSuccess = false })

        val relationship = client.loadPublicRelationship(" @Viewer ", " #Channel_Name ")

        assertEquals("2024-01-01T00:00:00Z", relationship.followedAt)
        assertEquals(false, relationship.subscriptionStatusHidden)
        assertEquals(true, relationship.isCurrentlySubscribed)
        assertEquals(18, relationship.subscriberMonths)
        assertEquals("2000", relationship.subscriberTier)
    }

    @Test
    fun enrichmentKeepsProfileWhenRelationshipProviderFails() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "api.twitch.tv") {
                respond(
                    content = ByteReadChannel(TWITCH_USER_JSON),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = ByteReadChannel("temporary outage"),
                    status = HttpStatusCode.ServiceUnavailable,
                )
            }
        }
        val client = TwitchUserCardClient(HttpClient(engine) { expectSuccess = false })

        val enrichment = client.enrich(
            authentication = authentication(),
            userId = "user-1",
            userLogin = "viewer",
            channelLogin = "channel_name",
        )

        assertEquals("Viewer", enrichment.user?.displayName)
        assertNull(enrichment.relationship)
    }

    @Test
    fun rejectsInvalidPublicRelationshipLoginBeforeNetwork() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(content = ByteReadChannel("{}"), status = HttpStatusCode.OK)
        }
        val client = TwitchUserCardClient(HttpClient(engine) { expectSuccess = false })

        assertFailsWith<IllegalArgumentException> {
            client.loadPublicRelationship("invalid-login!", "channel")
        }
        assertEquals(0, requests)
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
                  "broadcaster_type": "affiliate",
                  "description": "Profile description",
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
              "meta": {
                "tier": "2000"
              },
              "cumulative": {
                "months": 18
              }
            }
        """.trimIndent()
    }
}
