package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwitchEventSubTransportMaintenanceRuntimeTest {
    @Test
    fun summarizeGroupsEnabledWebsocketSubscriptionsBySession() {
        val runtime = TwitchEventSubTransportMaintenanceRuntime(
            client = TwitchEventSubSubscriptionClient(
                HttpClient(MockEngine { error("network is not used") }),
            ),
        )

        val snapshot = runtime.summarize(
            listOf(
                subscription("one", "enabled", "chat.message", "session-a"),
                subscription("two", "enabled", "chat.notice", "session-a"),
                subscription("three", "enabled", "automod.message.hold", "session-b"),
                subscription("four", "enabled", "chat.message", null, method = "webhook"),
                subscription("five", "websocket_disconnected", "chat.message", "session-c"),
            ),
        )

        assertEquals(2, snapshot.sessionCount)
        assertEquals(3, snapshot.subscriptionCount)
        assertEquals(listOf("session-a", "session-b"), snapshot.sessions.map { it.sessionId })
        assertEquals(2, snapshot.sessions.first().subscriptionCount)
        assertEquals(setOf("chat.message", "chat.notice"), snapshot.sessions.first().subscriptionTypes)
    }

    @Test
    fun cleanupDeletesOnlySessionsOtherThanCurrentOne() = runTest {
        val deleted = mutableListOf<String>()
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    content = ByteReadChannel(
                        """
                        {
                          "data": [
                            {
                              "id": "current-sub",
                              "status": "enabled",
                              "type": "channel.chat.message",
                              "version": "1",
                              "transport": {"method":"websocket","session_id":"session-current"}
                            },
                            {
                              "id": "stale-one",
                              "status": "enabled",
                              "type": "channel.chat.message",
                              "version": "1",
                              "transport": {"method":"websocket","session_id":"session-stale"}
                            },
                            {
                              "id": "stale-two",
                              "status": "enabled",
                              "type": "channel.chat.notification",
                              "version": "1",
                              "transport": {"method":"websocket","session_id":"session-stale"}
                            }
                          ],
                          "pagination": {}
                        }
                        """.trimIndent(),
                    ),
                    status = HttpStatusCode.OK,
                )
                HttpMethod.Delete -> {
                    deleted += requireNotNull(request.url.parameters["id"])
                    respond(ByteReadChannel(""), HttpStatusCode.NoContent)
                }
                else -> error("Unexpected method ${request.method}")
            }
        }
        val runtime = TwitchEventSubTransportMaintenanceRuntime(
            client = TwitchEventSubSubscriptionClient(
                HttpClient(engine) { expectSuccess = false },
            ),
        )

        val result = runtime.clearOtherSessions(
            authentication = authentication(),
            protectedSessionId = "session-current",
        )

        assertEquals(setOf("stale-one", "stale-two"), deleted.toSet())
        assertEquals(2, result.deletedSubscriptionCount)
        assertEquals(setOf("session-stale"), result.targetedSessionIds)
        assertTrue("current-sub" !in deleted)
    }

    private fun subscription(
        id: String,
        status: String,
        type: String,
        sessionId: String?,
        method: String = "websocket",
    ) = TwitchEventSubSubscriptionSnapshot(
        id = id,
        status = status,
        type = type,
        transport = TwitchEventSubSubscriptionTransportSnapshot(
            method = method,
            session_id = sessionId,
        ),
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
