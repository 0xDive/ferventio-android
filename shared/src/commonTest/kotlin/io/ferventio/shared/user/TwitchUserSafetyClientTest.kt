package io.ferventio.shared.user

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchUserSafetyClientTest {
    @Test
    fun blockUsesAuthenticatedHelixContract() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }
        val client = TwitchUserSafetyClient(HttpClient(engine) { expectSuccess = false })

        client.blockUser(
            authentication = authentication(),
            targetUserId = " target-id ",
        )

        val request = requireNotNull(captured)
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/helix/users/blocks", request.url.encodedPath)
        assertEquals("target-id", request.url.parameters["target_user_id"])
        assertEquals("chat", request.url.parameters["source_context"])
        assertEquals("other", request.url.parameters["reason"])
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("client-id", request.headers["Client-Id"])
    }

    @Test
    fun missingScopeAndSelfBlockFailBeforeNetwork() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount += 1
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }
        val client = TwitchUserSafetyClient(HttpClient(engine) { expectSuccess = false })

        val scopeError = assertFailsWith<TwitchUserSafetyScopeException> {
            client.blockUser(
                authentication = authentication(scopes = setOf("user:read:chat")),
                targetUserId = "target-id",
            )
        }
        assertEquals("user:manage:blocked_users", scopeError.requiredScope)

        assertFailsWith<IllegalArgumentException> {
            client.blockUser(
                authentication = authentication(),
                targetUserId = "viewer-id",
            )
        }
        assertEquals(0, requestCount)
    }

    @Test
    fun clientPreservesTwitchAuthenticationFailureDetails() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"Unauthorized","status":401,"message":"OAuth token is invalid"}""",
                ),
                status = HttpStatusCode.Unauthorized,
            )
        }
        val client = TwitchUserSafetyClient(HttpClient(engine) { expectSuccess = false })

        val error = assertFailsWith<TwitchUserSafetyMutationException> {
            client.blockUser(authentication(), "target-id")
        }

        assertEquals(401, error.statusCode)
        assertEquals("OAuth token is invalid", error.twitchMessage)
    }

    @Test
    fun runtimeMarksAuthenticationRequiredOnlyForAuthenticationFailure() = runTest {
        val state = ChatRuntimeStateHolder()
        val runtime = TwitchUserSafetyRuntime(
            chatState = state,
            gateway = FailingGateway(TwitchUserSafetyMutationException(401, "invalid token")),
        )

        assertFailsWith<TwitchUserSafetyMutationException> {
            runtime.blockUser(authentication(), "target-id")
        }

        assertTrue(state.authenticationRequired)

        val forbiddenState = ChatRuntimeStateHolder()
        val forbiddenRuntime = TwitchUserSafetyRuntime(
            chatState = forbiddenState,
            gateway = FailingGateway(TwitchUserSafetyMutationException(403, "forbidden")),
        )

        assertFailsWith<TwitchUserSafetyMutationException> {
            forbiddenRuntime.blockUser(authentication(), "target-id")
        }

        assertFalse(forbiddenState.authenticationRequired)
    }

    private class FailingGateway(
        private val failure: Throwable,
    ) : TwitchUserSafetyGateway {
        override suspend fun blockUser(
            authentication: StoredAuthentication,
            targetUserId: String,
        ) {
            throw failure
        }
    }

    private fun authentication(
        scopes: Set<String> = setOf("user:manage:blocked_users"),
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
                userId = "viewer-id",
                login = "viewer",
                scopes = scopes,
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
