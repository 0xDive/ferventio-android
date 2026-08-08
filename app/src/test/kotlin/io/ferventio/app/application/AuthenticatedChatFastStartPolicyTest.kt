package io.ferventio.app.application

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedChatFastStartPolicyTest {
    private val channels = listOf(
        ChatChannel(id = "100", login = "one", displayName = "One"),
        ChatChannel(id = "200", login = "two", displayName = "Two"),
    )

    @Test
    fun `starts from persisted twitch channels while metadata is refreshing`() {
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
    fun `does not start before cached channels are published`() {
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
    }

    @Test
    fun `does not start from anonymous placeholder ids`() {
        assertNull(
            AuthenticatedChatFastStartPolicy.candidateKey(
                isAuthenticated = true,
                isBootstrapping = false,
                isChannelsLoading = true,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                userId = "viewer",
                channels = listOf(
                    ChatChannel(id = "irc:one", login = "one", displayName = "One"),
                ),
            ),
        )
    }

    @Test
    fun `does not run after channel refresh is complete`() {
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
    fun `does not force fast start when transport already started or failed`() {
        val blockedStatuses = ConnectionStatus.entries.filterNot {
            it == ConnectionStatus.DISCONNECTED
        }

        blockedStatuses.forEach { status ->
            assertNull(
                "Fast start must not override $status transport state",
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
    fun `same cached snapshot is consumed only once while channels keep loading`() {
        val tracker = AuthenticatedChatFastStartAttemptTracker()

        assertEquals(
            "viewer:100,200",
            tracker.consumeCandidate(
                isAuthenticated = true,
                isChannelsLoading = true,
                candidateKey = "viewer:100,200",
            ),
        )
        assertNull(
            tracker.consumeCandidate(
                isAuthenticated = true,
                isChannelsLoading = true,
                candidateKey = null,
            ),
        )
        assertNull(
            tracker.consumeCandidate(
                isAuthenticated = true,
                isChannelsLoading = true,
                candidateKey = "viewer:100,200",
            ),
        )
    }

    @Test
    fun `completed loading resets one-shot tracker for a later restore`() {
        val tracker = AuthenticatedChatFastStartAttemptTracker()
        val key = "viewer:100,200"

        assertEquals(key, tracker.consumeCandidate(true, true, key))
        assertNull(tracker.consumeCandidate(true, false, null))
        assertEquals(key, tracker.consumeCandidate(true, true, key))
    }

    @Test
    fun `changed cached channel snapshot may start once in the same loading window`() {
        val tracker = AuthenticatedChatFastStartAttemptTracker()

        assertEquals("viewer:100", tracker.consumeCandidate(true, true, "viewer:100"))
        assertEquals("viewer:100,200", tracker.consumeCandidate(true, true, "viewer:100,200"))
        assertNull(tracker.consumeCandidate(true, true, "viewer:100,200"))
    }
}
