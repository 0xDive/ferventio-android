package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FerventioDiagnosticsActionsTest {
    @Test
    fun reconnectIsUnavailableByDefault() {
        assertFalse(FerventioDiagnosticsActions().reconnectAvailable)
    }

    @Test
    fun reconnectIsAvailableWhenHostProvidesAction() {
        assertTrue(
            FerventioDiagnosticsActions(
                onReconnect = {},
            ).reconnectAvailable,
        )
    }
}
