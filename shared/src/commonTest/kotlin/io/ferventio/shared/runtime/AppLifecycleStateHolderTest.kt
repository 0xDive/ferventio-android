package io.ferventio.shared.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLifecycleStateHolderTest {
    @Test
    fun defaultsToBackground() {
        val holder = AppLifecycleStateHolder()

        assertEquals(AppLifecyclePhase.BACKGROUND, holder.phase)
    }

    @Test
    fun tracksPlatformLifecycleTransitions() {
        val holder = AppLifecycleStateHolder(AppLifecyclePhase.INACTIVE)

        holder.markActive()
        assertEquals(AppLifecyclePhase.ACTIVE, holder.phase)

        holder.markInactive()
        assertEquals(AppLifecyclePhase.INACTIVE, holder.phase)

        holder.markBackground()
        assertEquals(AppLifecyclePhase.BACKGROUND, holder.phase)
    }
}
