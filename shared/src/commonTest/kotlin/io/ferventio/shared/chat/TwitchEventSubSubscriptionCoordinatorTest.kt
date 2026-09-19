package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwitchEventSubSubscriptionCoordinatorTest {
    @Test
    fun retriesConflictsWithAndroidBackoffPolicy() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val engine = MockEngine {
            calls += 1
            if (calls < 4) {
                respond(
                    content = ByteReadChannel("{\"message\":\"conflict\"}"),
                    status = HttpStatusCode.Conflict,
                )
            } else {
                respond(ByteReadChannel("{}"), HttpStatusCode.Accepted)
            }
        }
        val coordinator = TwitchEventSubSubscriptionCoordinator(
            client = TwitchEventSubSubscriptionClient(
                HttpClient(engine) { expectSuccess = false },
            ),
            delayAction = { millis -> delays += millis },
        )

        coordinator.createSubscription(
            authentication = authentication(),
            sessionId = "session-id",
            spec = TwitchEventSubSubscriptionPolicy.subscription(
                broadcasterId = "channel-id",
                type = "channel.chat.message",
            ),
        )

        assertEquals(4, calls)
        assertEquals(listOf(250L, 500L, 1_000L), delays)
    }

    @Test
    fun stopsAfterFourthConflict() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val engine = MockEngine {
            calls += 1
            respond(
                content = ByteReadChannel("{\"message\":\"still conflict\"}"),
                status = HttpStatusCode.Conflict,
            )
        }
        val coordinator = TwitchEventSubSubscriptionCoordinator(
            client = TwitchEventSubSubscriptionClient(
                HttpClient(engine) { expectSuccess = false },
            ),
            delayAction = { millis -> delays += millis },
        )

        val error = assertFailsWith<TwitchEventSubSubscriptionException> {
            coordinator.createSubscription(
                authentication = authentication(),
                sessionId = "session-id",
                spec = TwitchEventSubSubscriptionPolicy.subscription(
                    broadcasterId = "channel-id",
                    type = "channel.chat.message",
                ),
            )
        }

        assertEquals(409, error.statusCode)
        assertEquals(4, calls)
        assertEquals(listOf(250L, 500L, 1_000L), delays)
    }

    @Test
    fun doesNotRetryNonConflictErrors() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            respond(
                content = ByteReadChannel("{\"message\":\"forbidden\"}"),
                status = HttpStatusCode.Forbidden,
            )
        }
        val coordinator = TwitchEventSubSubscriptionCoordinator(
            client = TwitchEventSubSubscriptionClient(
                HttpClient(engine) { expectSuccess = false },
            ),
            delayAction = { error("delay must not run") },
        )

        val error = assertFailsWith<TwitchEventSubSubscriptionException> {
            coordinator.createSubscription(
                authentication = authentication(),
                sessionId = "session-id",
                spec = TwitchEventSubSubscriptionPolicy.subscription(
                    broadcasterId = "channel-id",
                    type = "channel.chat.message",
                ),
            )
        }

        assertEquals(403, error.statusCode)
        assertEquals(1, calls)
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
