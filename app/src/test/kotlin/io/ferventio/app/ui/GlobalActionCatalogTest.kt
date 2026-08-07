package io.ferventio.app.ui

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.CustomCommand
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.SearchableActionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalActionCatalogTest {
    @Test
    fun `catalog includes navigation channels built ins and custom commands`() {
        val state = FerventioUiState(
            channels = listOf(
                ChatChannel(id = "123", login = "streamer", displayName = "Streamer"),
            ),
            customCommands = listOf(
                CustomCommand(name = "hello", template = "Hello chat"),
            ),
        )

        val actions = GlobalActionCatalog.build(state, catalogStrings())

        assertNotNull(actions.firstOrNull { it.id == "navigation:settings" })
        assertNotNull(actions.firstOrNull { it.id == "navigation:add-channel" })
        assertNotNull(actions.firstOrNull { it.id == "channel:123" })
        assertNotNull(actions.firstOrNull { it.id == "command:timeout" })
        assertNotNull(actions.firstOrNull { it.id == "command:hello" })
    }

    @Test
    fun `disabled custom commands are excluded`() {
        val state = FerventioUiState(
            customCommands = listOf(
                CustomCommand(name = "enabled", template = "hello", enabled = true),
                CustomCommand(name = "disabled", template = "hello", enabled = false),
            ),
        )

        val ids = GlobalActionCatalog.build(state, catalogStrings()).map { it.id }.toSet()

        assertTrue("command:enabled" in ids)
        assertFalse("command:disabled" in ids)
    }

    @Test
    fun `custom moderation macros retain safety metadata`() {
        val state = FerventioUiState(
            customCommands = listOf(
                CustomCommand(name = "spam", template = "/timeout {user} 600 spam"),
                CustomCommand(name = "wave", template = "/nuke {1+}"),
            ),
        )

        val actions = GlobalActionCatalog.build(state, catalogStrings())
        val timeout = actions.single { it.id == "command:spam" }
        val nuke = actions.single { it.id == "command:wave" }

        assertEquals(SearchableActionKind.MODERATION, timeout.kind)
        assertTrue(timeout.requiresConfirmation)
        assertFalse(timeout.requiresPreview)
        assertEquals(SearchableActionKind.MODERATION, nuke.kind)
        assertTrue(nuke.requiresConfirmation)
        assertTrue(nuke.requiresPreview)
    }

    @Test
    fun `catalog ids are unique`() {
        val state = FerventioUiState(
            customCommands = listOf(CustomCommand(name = "hello", template = "Hello")),
        )

        val actions = GlobalActionCatalog.build(state, catalogStrings())

        assertEquals(actions.size, actions.map { it.id }.distinct().size)
    }

    @Test
    fun `catalog uses supplied localized navigation copy`() {
        val actions = GlobalActionCatalog.build(
            FerventioUiState(),
            catalogStrings(settingsTitle = "Settings localized"),
        )

        assertEquals(
            "Settings localized",
            actions.single { it.id == "navigation:settings" }.title,
        )
    }

    private fun catalogStrings(settingsTitle: String = "Settings") = GlobalActionCatalogStrings(
        settingsTitle = settingsTitle,
        settingsSubtitle = "Open settings",
        addChannelTitle = "Add channel",
        addChannelSubtitle = "Connect channel",
        reconnectTitle = "Reconnect chat",
        reconnectSubtitle = "Restart EventSub",
    )
}
