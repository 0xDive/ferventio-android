package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FerventioAccountActionsTest {
    @Test
    fun accountManagementIsUnavailableByDefault() {
        assertFalse(FerventioAccountActions().accountManagementAvailable)
    }

    @Test
    fun accountManagementRequiresEveryTransactionalAction() {
        val reauthorize = {}
        val revokeDevice = {}
        val revokeAllSessions = {}

        assertFalse(
            FerventioAccountActions(
                onReauthorize = reauthorize,
                onRevokeDevice = revokeDevice,
            ).accountManagementAvailable,
        )
        assertFalse(
            FerventioAccountActions(
                onReauthorize = reauthorize,
                onRevokeAllSessions = revokeAllSessions,
            ).accountManagementAvailable,
        )
        assertFalse(
            FerventioAccountActions(
                onRevokeDevice = revokeDevice,
                onRevokeAllSessions = revokeAllSessions,
            ).accountManagementAvailable,
        )
        assertTrue(
            FerventioAccountActions(
                onReauthorize = reauthorize,
                onRevokeDevice = revokeDevice,
                onRevokeAllSessions = revokeAllSessions,
            ).accountManagementAvailable,
        )
    }
}
