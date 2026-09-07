package io.ferventio.shared.runtime

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.push.PushAuthorizationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class FerventioRuntimeStateTest {
    @Test
    fun ownsLifecycleWorkspaceAndPushStateForPlatformAdapters() {
        val runtime = FerventioRuntimeState()

        runtime.lifecycle.markActive()
        runtime.workspace.addOrReplaceChannel(
            ChatChannel(
                id = "channel-id",
                login = "channel",
                displayName = "Channel",
            ),
        )
        runtime.pushRegistration.updateAuthorizationStatus(PushAuthorizationStatus.AUTHORIZED)

        assertEquals(AppLifecyclePhase.ACTIVE, runtime.lifecycle.phase)
        assertEquals(listOf("channel-id"), runtime.workspace.channelIds)
        assertEquals(PushAuthorizationStatus.AUTHORIZED, runtime.pushRegistration.authorizationStatus)
    }
}
