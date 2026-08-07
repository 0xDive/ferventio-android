package io.ferventio.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveChatCapabilitiesTest {
    @Test
    fun `manage scopes enable own-channel interactive management`() {
        val session = session(
            userId = "42",
            scopes = setOf("channel:manage:polls", "channel:manage:predictions"),
        )

        val capabilities = session.interactiveChatCapabilities("42")

        assertTrue(capabilities.canReadPolls)
        assertTrue(capabilities.canManagePolls)
        assertTrue(capabilities.canReadPredictions)
        assertTrue(capabilities.canManagePredictions)
    }

    @Test
    fun `read scopes do not enable management`() {
        val session = session(
            userId = "42",
            scopes = setOf("channel:read:polls", "channel:read:predictions"),
        )

        val capabilities = session.interactiveChatCapabilities("42")

        assertTrue(capabilities.canReadPolls)
        assertFalse(capabilities.canManagePolls)
        assertTrue(capabilities.canReadPredictions)
        assertFalse(capabilities.canManagePredictions)
    }

    @Test
    fun `interactive capabilities are disabled for another broadcaster`() {
        val session = session(
            userId = "42",
            scopes = setOf("channel:manage:polls", "channel:manage:predictions"),
        )

        val capabilities = session.interactiveChatCapabilities("99")

        assertFalse(capabilities.canReadPolls)
        assertFalse(capabilities.canManagePolls)
        assertFalse(capabilities.canReadPredictions)
        assertFalse(capabilities.canManagePredictions)
    }

    private fun session(userId: String, scopes: Set<String>) = TwitchSession(
        clientId = "client",
        userId = userId,
        login = "streamer",
        scopes = scopes,
        expiresInSeconds = 3_600,
    )
}
