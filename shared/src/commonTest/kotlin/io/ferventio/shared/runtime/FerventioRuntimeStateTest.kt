package io.ferventio.shared.runtime

import io.ferventio.shared.push.PushAuthorizationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class FerventioRuntimeStateTest {
    @Test
    fun ownsLifecycleAndPushStateForPlatformAdapters() {
        val runtime = FerventioRuntimeState()

        runtime.lifecycle.markActive()
        runtime.pushRegistration.updateAuthorizationStatus(PushAuthorizationStatus.AUTHORIZED)

        assertEquals(AppLifecyclePhase.ACTIVE, runtime.lifecycle.phase)
        assertEquals(PushAuthorizationStatus.AUTHORIZED, runtime.pushRegistration.authorizationStatus)
    }
}
