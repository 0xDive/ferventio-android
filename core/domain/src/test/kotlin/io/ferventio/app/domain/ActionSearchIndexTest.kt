package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSearchIndexTest {
    @Test
    fun `exact title beats keyword-only match`() {
        val actions = listOf(
            SearchableAction(
                id = "setting:timestamps",
                title = "Chat timestamps",
                keywords = setOf("time", "chat"),
                kind = SearchableActionKind.SETTING,
            ),
            SearchableAction(
                id = "setting:clock",
                title = "Clock format",
                keywords = setOf("timestamps"),
                kind = SearchableActionKind.SETTING,
            ),
        )

        val result = ActionSearchIndex.search("chat timestamps", actions)

        assertEquals("setting:timestamps", result.first().action.id)
    }

    @Test
    fun `all query tokens must match searchable fields`() {
        val actions = listOf(
            SearchableAction(
                id = "setting:theme",
                title = "Dark theme",
                keywords = setOf("appearance"),
                kind = SearchableActionKind.SETTING,
            ),
            SearchableAction(
                id = "setting:chat-theme",
                title = "Chat colors",
                keywords = setOf("dark", "appearance"),
                kind = SearchableActionKind.SETTING,
            ),
        )

        val result = ActionSearchIndex.search("dark chat", actions)

        assertEquals(listOf("setting:chat-theme"), result.map { it.action.id })
    }

    @Test
    fun `custom moderation command is exposed as moderation action`() {
        val action = SearchableActionFactory.fromCustomCommand(
            CustomCommand(
                name = "spam",
                template = "/timeout {user} 600 spam",
                description = "Timeout selected spammer",
            ),
        )

        assertEquals(SearchableActionKind.MODERATION, action.kind)
        assertTrue(action.requiresConfirmation)
    }

    @Test
    fun `mass moderation custom command carries preview requirement`() {
        val action = SearchableActionFactory.fromCustomCommand(
            CustomCommand(
                name = "wave",
                template = "/nuke {1+}",
            ),
        )

        assertEquals(SearchableActionKind.MODERATION, action.kind)
        assertTrue(action.requiresPreview)
        assertTrue(action.requiresConfirmation)
    }

    @Test
    fun `built in aliases participate in search`() {
        val definition = CommandDefinition(
            name = "user",
            usage = "/user login",
            description = "Open user card",
            aliases = setOf("usercard"),
        )
        val action = SearchableActionFactory.fromCommandDefinition(definition)

        val result = ActionSearchIndex.search("usercard", listOf(action))

        assertEquals("command:user", result.single().action.id)
    }
}
