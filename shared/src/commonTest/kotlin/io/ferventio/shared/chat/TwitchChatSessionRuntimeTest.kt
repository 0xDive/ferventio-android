package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchChatSessionRuntimeTest {
    @Test
    fun primaryEnvelopeIsAppendedToSharedChatState() {
        val state = ChatRuntimeStateHolder()
        val runtime = runtime(state)
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {
                "message_id": "event-1",
                "message_type": "notification",
                "message_timestamp": "2026-08-16T17:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "channel-1",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "viewer-1",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "message-1",
                  "message_type": "text",
                  "badges": [],
                  "message": {"text": "Hello from shared EventSub"}
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(runtime.onEnvelope(envelope))
        val message = state.messages("channel-1").single()
        assertEquals("message-1", message.id)
        assertEquals("event-1", message.eventSubMessageId)
        assertEquals("Hello from shared EventSub", message.text)
    }

    @Test
    fun malformedPrimaryNotificationDoesNotDisconnectRuntime() {
        val state = ChatRuntimeStateHolder()
        val runtime = runtime(state)
        val envelope = TwitchEventSubProtocolParser.parse(
            """
            {
              "metadata": {"message_type": "notification"},
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": null
              }
            }
            """.trimIndent(),
        )

        assertFalse(runtime.onEnvelope(envelope))
        assertTrue(state.messagesByChannel.isEmpty())
    }

    @Test
    fun sessionReadyBootstrapsMinimumThenSchedulesRemainingSubscriptions() = runTest {
        val channel = channel()
        val calls = mutableListOf<TwitchEventSubSubscriptionSpec>()
        val bootstrap = TwitchEventSubBootstrapCoordinator { _, _, spec -> calls += spec }
        val runtime = TwitchChatSessionRuntime(
            authentication = authentication(),
            workspace = WorkspaceRuntimeSnapshot(channels = listOf(channel)),
            state = ChatRuntimeStateHolder(),
            bootstrapCoordinator = bootstrap,
        )

        val initialCount = runtime.onSessionReady("socket-session")
        assertEquals(2, initialCount)
        assertEquals(
            listOf(
                TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
                TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE,
            ),
            calls.take(2).map(TwitchEventSubSubscriptionSpec::type),
        )

        advanceUntilIdle()
        val expectedTypes = TwitchEventSubSubscriptionPolicy.BASE_EVENT_TYPES
            .filterNot { it in TwitchEventSubSubscriptionPolicy.MODERATOR_EVENT_TYPES }
            .toSet()
        assertEquals(
            expectedTypes,
            calls.map(TwitchEventSubSubscriptionSpec::type).toSet(),
        )
    }

    @Test
    fun connectionUpdatesAreReflectedInSharedState() {
        val state = ChatRuntimeStateHolder()
        val runtime = runtime(state)

        runtime.onConnectionUpdate(
            TwitchEventSubConnectionUpdate(
                status = ConnectionStatus.RECONNECTING,
                attempt = 3,
                error = "network reset",
            ),
        )

        assertEquals(ConnectionStatus.RECONNECTING, state.connectionStatus)
        assertEquals(3, state.connectionAttempt)
        assertEquals("network reset", state.connectionErrorMessage)
    }

    private fun runtime(state: ChatRuntimeStateHolder) = TwitchChatSessionRuntime(
        authentication = authentication(),
        workspace = WorkspaceRuntimeSnapshot(channels = listOf(channel())),
        state = state,
        bootstrapCoordinator = TwitchEventSubBootstrapCoordinator { _, _, _ -> Unit },
    )

    private fun channel() = ChatChannel(
        id = "channel-1",
        login = "channel",
        displayName = "Channel",
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
