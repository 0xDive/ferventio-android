package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchEventSubSupplementalSubscriptionRuntimeTest {
    @Test
    fun authenticationFailureDuringSupplementalSubscriptionsRequiresReauthentication() = runTest {
        val state = ChatRuntimeStateHolder()
        state.updateConnection(ConnectionStatus.CONNECTED)
        var calls = 0
        var fatalError: Throwable? = null
        val runtime = runtime(
            state = state,
            onFatalSessionError = { fatalError = it },
        ) { _, _, _ ->
            calls += 1
            if (calls == 3) {
                throw TwitchEventSubSubscriptionException(
                    statusCode = 401,
                    twitchMessage = "OAuth token is invalid",
                )
            }
        }

        assertEquals(2, runtime.onSessionReady("socket-session"))
        runCurrent()

        assertEquals(3, calls)
        assertTrue(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
        assertTrue(state.connectionErrorMessage.orEmpty().contains("OAuth token is invalid"))
        assertIs<TwitchEventSubSubscriptionException>(fatalError)
    }

    @Test
    fun nonAuthenticationSupplementalFailureKeepsPrimaryChatConnected() = runTest {
        val state = ChatRuntimeStateHolder()
        state.updateConnection(ConnectionStatus.CONNECTED)
        var calls = 0
        var fatalError: Throwable? = null
        val runtime = runtime(
            state = state,
            onFatalSessionError = { fatalError = it },
        ) { _, _, _ ->
            calls += 1
            if (calls == 3) {
                throw TwitchEventSubSubscriptionException(
                    statusCode = 500,
                    twitchMessage = "temporary Twitch failure",
                )
            }
        }

        assertEquals(2, runtime.onSessionReady("socket-session"))
        runCurrent()

        assertTrue(calls > 3)
        assertFalse(state.authenticationRequired)
        assertEquals(ConnectionStatus.CONNECTED, state.connectionStatus)
        assertTrue(
            state.connectionErrorMessage.orEmpty().contains("channel.chat.message_delete"),
        )
        assertTrue(state.connectionErrorMessage.orEmpty().contains("temporary Twitch failure"))
        assertNull(fatalError)
    }

    private fun runtime(
        state: ChatRuntimeStateHolder,
        onFatalSessionError: (Throwable) -> Unit = {},
        createSubscriptionAction: suspend (
            StoredAuthentication,
            String,
            TwitchEventSubSubscriptionSpec,
        ) -> Unit,
    ) = TwitchChatSessionRuntime(
        authentication = authentication(),
        workspace = WorkspaceRuntimeSnapshot(channels = listOf(channel())),
        state = state,
        bootstrapCoordinator = TwitchEventSubBootstrapCoordinator(createSubscriptionAction),
        onFatalSessionError = onFatalSessionError,
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
