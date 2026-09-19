package io.ferventio.shared.ui.app

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.CustomCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedGlobalActionSearchCatalogTest {
    private val strings = SharedGlobalActionCatalogStrings(
        settingsTitle = "Settings",
        settingsSubtitle = "Open settings",
        addChannelTitle = "Add channel",
        addChannelSubtitle = "Add Twitch channel",
        reconnectTitle = "Reconnect",
        reconnectSubtitle = "Reconnect EventSub",
    )
    private val channels = listOf(
        ChatChannel(id = "1", login = "alpha", displayName = "Alpha"),
        ChatChannel(id = "2", login = "beta", displayName = "Beta"),
    )

    @Test
    fun catalogContainsNavigationAndChannels() {
        val actions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = emptySet(),
            activeChannelId = "1",
            strings = strings,
            canAddChannel = true,
            reconnectAvailable = true,
        )

        assertTrue(actions.any { it.id == SHARED_ACTION_ID_SETTINGS })
        assertTrue(actions.any { it.id == SHARED_ACTION_ID_ADD_CHANNEL })
        assertTrue(actions.any { it.id == SHARED_ACTION_ID_RECONNECT })
        assertTrue(actions.any { it.id == "channel:1" })
        assertTrue(actions.any { it.id == "command:me" })
        assertFalse(actions.any { it.id == "command:ban" })
    }

    @Test
    fun moderatorCatalogIncludesSupportedModerationCommands() {
        val actions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = setOf("1"),
            activeChannelId = "1",
            strings = strings,
            canAddChannel = true,
            reconnectAvailable = true,
        )

        assertTrue(actions.any { it.id == "command:ban" })
        assertTrue(actions.any { it.id == "command:slow" })
        assertTrue(actions.any { it.id == "command:nuke" })
    }

    @Test
    fun customCommandsRespectEnabledStateAndModeratorAccess() {
        val commands = listOf(
            CustomCommand(
                name = "hello",
                template = "Hello {1}",
                description = "Safe greeting",
            ),
            CustomCommand(
                name = "punish",
                template = "/timeout {1} 60 custom macro",
                description = "Moderator macro",
            ),
            CustomCommand(
                name = "disabled",
                template = "Hidden",
                enabled = false,
            ),
        )

        val viewerActions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = emptySet(),
            activeChannelId = "1",
            strings = strings,
            canAddChannel = true,
            reconnectAvailable = true,
            customCommands = commands,
        )
        assertTrue(viewerActions.any { it.id == "command:hello" })
        assertFalse(viewerActions.any { it.id == "command:punish" })
        assertFalse(viewerActions.any { it.id == "command:disabled" })

        val moderatorActions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = setOf("1"),
            activeChannelId = "1",
            strings = strings,
            canAddChannel = true,
            reconnectAvailable = true,
            customCommands = commands,
        )
        val moderationMacro = moderatorActions.single { it.id == "command:punish" }
        assertTrue(moderationMacro.requiresConfirmation)
    }

    @Test
    fun commandsStayHiddenUntilSlashMode() {
        val actions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = setOf("1"),
            activeChannelId = "1",
            strings = strings,
            canAddChannel = true,
            reconnectAvailable = true,
        )

        assertFalse(visibleSharedGlobalActions("ban", actions).any { it.action.id.startsWith("command:") })
        assertEquals(
            "command:ban",
            visibleSharedGlobalActions("/ban", actions).first().action.id,
        )
    }

    @Test
    fun channelSearchUsesSharedRanking() {
        val actions = buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = emptySet(),
            activeChannelId = null,
            strings = strings,
            canAddChannel = false,
            reconnectAvailable = false,
        )

        assertEquals(
            "channel:1",
            visibleSharedGlobalActions("alpha", actions).first().action.id,
        )
        assertFalse(actions.any { it.id.startsWith("command:") })
    }
}
