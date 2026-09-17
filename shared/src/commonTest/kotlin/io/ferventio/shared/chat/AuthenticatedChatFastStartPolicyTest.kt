package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthenticatedChatFastStartPolicyTest {
    private val channels = listOf(
        ChatChannel(id = "100", login = "one", displayName = "One"),
        ChatChannel(id = "200", login = "two", displayName = "Two"),
    )

    @Test
    fun startsFromPersistedTwitchChannelsWhileMetadataRefreshes() {
        assertEquals(
            "viewer:100,200",
            AuthenticatedChatFastStartPolicy.candidateKey(
                isAuthenticated = true,
                isBootstrapping = false,
                isChannelsLoading = true,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                userId = "viewer",
                channels = channels,
            ),
        )
    }

    @Test
    fun rejectsBootstrapAnonymousAndCompletedRefreshSnapshots() {
        assertNull(
            AuthenticatedChatFastStartPolicy.candidateKey(
                isAuthenticated = true,
                isBootstrapping = true,
                isChannelsLoading = true,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                userId = "viewer",
                channels = channels,
            ),
        )
        assertNull(
            AuthenticatedChatFastStartPolicy.candidateKey(
                isAuthenticated = true,
                isBootstrapping = false,
                isChannelsLoading = true,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                userId = "viewer",
                channels = listOf(ChatChannel("irc:one", "one", "One")),
            ),
        )
        assertNull(
            AuthenticatedChatFastStartPolicy.candidateKey(
                isAuthenticated = true,
                isBootstrapping = false,
                isChannelsLoading = false,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                userId = "viewer",
                channels = channels,
            ),
        )
    }

    @Test
    fun requiresDisconnectedTransport() {
        ConnectionStatus.entries
            .filterNot { status -> status == ConnectionStatus.DISCONNECTED }
            .forEach { status ->
                assertNull(
                    AuthenticatedChatFastStartPolicy.candidateKey(
                        isAuthenticated = true,
                        isBootstrapping = false,
                        isChannelsLoading = true,
                        connectionStatus = status,
                        userId = "viewer",
                        channels = channels,
                    ),
                )
            }
    }

    @Test
    fun trackerConsumesSnapshotOncePerLoadingWindow() {
        val tracker = AuthenticatedChatFastStartAttemptTracker()
        val key = "viewer:100,200"

        assertEquals(key, tracker.consumeCandidate(true, true, key))
        assertNull(tracker.consumeCandidate(true, true, null))
        assertNull(tracker.consumeCandidate(true, true, key))
        assertNull(tracker.consumeCandidate(true, false, null))
        assertEquals(key, tracker.consumeCandidate(true, true, key))
    }

    @Test
    fun trackerAllowsChangedSnapshotDuringSameLoadingWindow() {
        val tracker = AuthenticatedChatFastStartAttemptTracker()

        assertEquals("viewer:100", tracker.consumeCandidate(true, true, "viewer:100"))
        assertEquals("viewer:100,200", tracker.consumeCandidate(true, true, "viewer:100,200"))
        assertNull(tracker.consumeCandidate(true, true, "viewer:100,200"))
    }
}
