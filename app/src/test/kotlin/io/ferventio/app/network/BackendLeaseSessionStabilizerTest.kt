package io.ferventio.app.network

import io.ferventio.app.domain.TwitchSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class BackendLeaseSessionStabilizerTest {
    @After
    fun tearDown() {
        BackendLeaseSessionStabilizer.clearForTest()
    }

    @Test
    fun `expiry countdown alone keeps the same transport session`() {
        val first = session(expiresInSeconds = 7_200)
        val renewed = session(expiresInSeconds = 7_155)

        val stableFirst = BackendLeaseSessionStabilizer.stabilize("token-a", first)
        val stableRenewed = BackendLeaseSessionStabilizer.stabilize("token-a", renewed)

        assertSame(stableFirst, stableRenewed)
        assertEquals(7_200L, stableRenewed.expiresInSeconds)
    }

    @Test
    fun `new access token replaces the transport session`() {
        val first = BackendLeaseSessionStabilizer.stabilize("token-a", session(7_200))
        val refreshed = BackendLeaseSessionStabilizer.stabilize("token-b", session(14_400))

        assertNotSame(first, refreshed)
        assertEquals(14_400L, refreshed.expiresInSeconds)
    }

    @Test
    fun `scope change replaces the transport session even with the same token`() {
        val first = BackendLeaseSessionStabilizer.stabilize("token-a", session(7_200))
        val changed = BackendLeaseSessionStabilizer.stabilize(
            "token-a",
            session(7_155, scopes = setOf("user:read:chat", "moderator:manage:chat_messages")),
        )

        assertNotSame(first, changed)
    }

    private fun session(
        expiresInSeconds: Long,
        scopes: Set<String> = setOf("user:read:chat"),
    ) = TwitchSession(
        clientId = "client",
        userId = "viewer",
        login = "viewer",
        scopes = scopes,
        expiresInSeconds = expiresInSeconds,
    )
}
