package io.ferventio.shared.settings

import io.ferventio.app.domain.CustomCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedAppSettingsCustomCommandsTest {
    @Test
    fun restoreAndClearKeepCustomCommandsInSharedSettingsState() {
        val holder = SharedAppSettingsStateHolder()
        holder.restore(
            preferences = SharedAppPreferences(),
            revision = 7L,
            customCommands = listOf(
                CustomCommand(name = "zeta", template = "Z"),
                CustomCommand(name = "alpha", template = "A"),
            ),
        )

        assertEquals(listOf("alpha", "zeta"), holder.customCommands.map { it.normalizedName })
        assertEquals(7L, holder.syncRevision)

        holder.clear()

        assertTrue(holder.customCommands.isEmpty())
        assertEquals(0L, holder.syncRevision)
    }
}
