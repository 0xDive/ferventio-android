package io.ferventio.shared.chat

import io.ferventio.app.domain.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAuthenticationRuntimeStateTest {
    @Test
    fun authenticationFailurePersistsUntilExplicitlyCleared() {
        val state = ChatRuntimeStateHolder()

        state.markAuthenticationRequired("Unauthorized")

        assertTrue(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
        assertEquals("Unauthorized", state.connectionErrorMessage)

        state.clearAuthenticationRequired()

        assertFalse(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
    }

    @Test
    fun clearingRuntimeAlsoClearsAuthenticationFailure() {
        val state = ChatRuntimeStateHolder()
        state.markAuthenticationRequired("Forbidden")

        state.clear()

        assertFalse(state.authenticationRequired)
        assertEquals(ConnectionStatus.DISCONNECTED, state.connectionStatus)
    }
}
