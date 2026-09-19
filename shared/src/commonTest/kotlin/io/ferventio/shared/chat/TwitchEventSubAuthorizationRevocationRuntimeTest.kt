package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwitchEventSubAuthorizationRevocationRuntimeTest {
    @Test
    fun authorizationRevocationMarksSharedRuntimeForReauthentication() {
        val state = ChatRuntimeStateHolder()
        val runtime = TwitchChatSessionRuntime(
            authentication = authentication(),
            workspace = WorkspaceRuntimeSnapshot(channels = listOf(channel())),
            state = state,
            bootstrapCoordinator = TwitchEventSubBootstrapCoordinator { _, _, _ -> Unit },
        )

        val handled = runtime.onEnvelope(
            TwitchEventSubProtocolEnvelope(
                type = "revocation",
                subscriptionType = "channel.chat.message",
                revocationStatus = TwitchEventSubConnectionPolicy.AUTHORIZATION_REVOKED,
            ),
        )

        assertTrue(handled)
        assertTrue(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
        assertTrue(state.connectionErrorMessage.orEmpty().contains("authorization was revoked"))
        assertTrue(state.connectionErrorMessage.orEmpty().contains("channel.chat.message"))
    }

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
