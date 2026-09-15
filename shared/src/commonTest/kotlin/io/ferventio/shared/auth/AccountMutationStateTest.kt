package io.ferventio.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountMutationStateTest {
    @Test
    fun accountMutationAllowsOnlyOneActionAtATime() {
        val state = AccountMutationStateHolder()

        assertTrue(state.beginReauthorization())
        assertFalse(state.beginRevokeDevice())
        assertFalse(state.beginRevokeAllSessions())
        assertTrue(state.state.isReauthorizing)
        assertTrue(state.state.mutationInFlight)
    }

    @Test
    fun revokeAllMutationRejectsDuplicateStart() {
        val state = AccountMutationStateHolder()

        assertTrue(state.beginRevokeAllSessions())
        assertFalse(state.beginRevokeAllSessions())
        assertFalse(state.beginReauthorization())
        assertTrue(state.state.isRevokingAllSessions)
    }

    @Test
    fun mutationFailureKeepsErrorWithoutBusyState() {
        val state = AccountMutationStateHolder()
        state.beginRevokeAllSessions()

        state.failMutation("  backend unavailable  ")

        assertFalse(state.state.mutationInFlight)
        assertEquals("backend unavailable", state.state.errorMessage)
    }

    @Test
    fun mutationSuccessClearsMutationState() {
        val state = AccountMutationStateHolder()
        state.beginRevokeDevice()

        state.finishMutation()

        assertEquals(AccountMutationState(), state.state)
    }
}
