package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchEventSubBootstrapCoordinatorTest {
    @Test
    fun fallsBackToSecondPrimaryAndLeavesOptionalWork() = runTest {
        val first = channel("one", "first-login")
        val second = channel("two", "second-login")
        val calls = mutableListOf<Pair<String, String>>()
        val coordinator = TwitchEventSubBootstrapCoordinator { _, _, spec ->
            calls += spec.broadcasterId to spec.type
            when {
                spec.broadcasterId == first.id &&
                    spec.type == TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE ->
                    error("first primary unavailable")

                spec.broadcasterId == second.id &&
                    spec.type == TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE ->
                    error("second notice unavailable")
            }
        }

        val result = coordinator.bootstrap(
            authentication = authentication(),
            sessionId = "socket-session",
            channels = listOf(first, second),
            moderatedChannelIds = emptySet(),
        )

        assertEquals(second, result.connectedChannel)
        assertEquals(1, result.subscriptionCount)
        assertFalse(result.noticeReady)
        assertEquals(
            listOf(
                first.id to TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
                second.id to TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
                second.id to TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE,
            ),
            calls,
        )
        assertTrue(
            result.remainingSubscriptions.any {
                it.broadcasterId == first.id &&
                    it.type == TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE
            },
        )
        assertTrue(
            result.remainingSubscriptions.any {
                it.broadcasterId == second.id &&
                    it.type == TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE
            },
        )
        assertEquals(listOf("first-login", "second-login"), result.failures.map { it.channel.login })
    }

    @Test
    fun unauthorizedPrimaryStopsChannelFallback() = runTest {
        val first = channel("one", "first-login")
        val second = channel("two", "second-login")
        val calls = mutableListOf<Pair<String, String>>()
        val coordinator = TwitchEventSubBootstrapCoordinator { _, _, spec ->
            calls += spec.broadcasterId to spec.type
            throw TwitchEventSubSubscriptionException(
                statusCode = 401,
                twitchMessage = "OAuth token is invalid",
            )
        }

        val error = assertFailsWith<TwitchEventSubBootstrapException> {
            coordinator.bootstrap(
                authentication = authentication(),
                sessionId = "socket-session",
                channels = listOf(first, second),
                moderatedChannelIds = emptySet(),
            )
        }

        assertEquals(
            listOf(first.id to TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE),
            calls,
        )
        assertTrue(error.isTwitchAuthenticationFailure())
        assertEquals("first-login", error.failures.single().channel.login)
    }

    @Test
    fun forbiddenPrimaryCanFallBackToNextChannel() = runTest {
        val first = channel("one", "first-login")
        val second = channel("two", "second-login")
        val calls = mutableListOf<Pair<String, String>>()
        val coordinator = TwitchEventSubBootstrapCoordinator { _, _, spec ->
            calls += spec.broadcasterId to spec.type
            if (
                spec.broadcasterId == first.id &&
                spec.type == TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE
            ) {
                throw TwitchEventSubSubscriptionException(
                    statusCode = 403,
                    twitchMessage = "subscription missing proper authorization",
                )
            }
        }

        val result = coordinator.bootstrap(
            authentication = authentication(),
            sessionId = "socket-session",
            channels = listOf(first, second),
            moderatedChannelIds = emptySet(),
        )

        assertEquals(second, result.connectedChannel)
        assertTrue(result.noticeReady)
        assertEquals(2, result.subscriptionCount)
        assertEquals(
            listOf(
                first.id to TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
                second.id to TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
                second.id to TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE,
            ),
            calls,
        )
        assertFalse(result.failures.single().cause!!.isTwitchAuthenticationFailure())
    }

    @Test
    fun remainingSubscriptionsAreBestEffortAndKeepChannelMetadata() = runTest {
        val first = channel("one", "first-login")
        val second = channel("two", "second-login")
        var bootstrapFinished = false
        val calls = mutableListOf<Pair<String, String>>()
        val coordinator = TwitchEventSubBootstrapCoordinator { _, _, spec ->
            calls += spec.broadcasterId to spec.type
            if (bootstrapFinished &&
                spec.broadcasterId == second.id &&
                spec.type == TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE
            ) {
                error("second channel unavailable")
            }
        }
        val result = coordinator.bootstrap(
            authentication = authentication(),
            sessionId = "socket-session",
            channels = listOf(first, second),
            moderatedChannelIds = emptySet(),
        )
        assertTrue(result.noticeReady)
        assertEquals(2, result.subscriptionCount)

        bootstrapFinished = true
        val failures = coordinator.createRemaining(
            authentication = authentication(),
            sessionId = "socket-session",
            bootstrap = result,
        )

        assertEquals(1, failures.size)
        assertEquals("second-login", failures.single().channel.login)
        assertEquals(TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE, failures.single().type)
        assertTrue(
            calls.any {
                it.first == second.id &&
                    it.second == TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE
            },
        )
    }

    private fun channel(id: String, login: String) = ChatChannel(
        id = id,
        login = login,
        displayName = login,
    )

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
