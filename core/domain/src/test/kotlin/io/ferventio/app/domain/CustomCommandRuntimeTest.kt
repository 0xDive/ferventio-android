package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCommandRuntimeTest {
    private val baseContext = CustomCommandContext(
        channelName = "Streamer",
        channelId = "channel-id",
        myName = "me",
        myId = "my-id",
        streamTitle = "Current title",
        streamGame = "Current game",
    )

    @Test
    fun `expands selected user reply and clipboard variables`() {
        val command = CustomCommand(
            name = "context",
            template = "{user} {user.id} {reply.user} {reply.user.id} {reply.text} {clipboard}",
        )
        val context = CustomCommandRuntimeContext(
            base = baseContext,
            selectedUser = CustomCommandUser("user-id", "viewer", "Viewer"),
            reply = CustomCommandReply(
                messageId = "message-id",
                user = CustomCommandUser("reply-id", "replyuser", "Reply User"),
                text = "hello there",
            ),
            clipboardText = "copied text",
        )

        val result = CustomCommandRuntimeExpander.expand(command, emptyList(), context)

        assertEquals(
            "viewer user-id replyuser reply-id hello there copied text",
            (result as CustomCommandExpansionResult.Success).value,
        )
    }

    @Test
    fun `returns error when required runtime context is missing`() {
        val command = CustomCommand(name = "so", template = "Check out {user}")

        val result = CustomCommandRuntimeExpander.expand(
            command = command,
            arguments = emptyList(),
            context = CustomCommandRuntimeContext(base = baseContext),
        )

        assertTrue(result is CustomCommandExpansionResult.Error)
    }

    @Test
    fun `plain text macro is safe`() {
        val plan = CustomCommandSafety.executionPlan("Hello chat")

        assertEquals(CustomCommandRisk.SAFE_TEXT, plan.risk)
        assertFalse(plan.requiresPreview)
        assertFalse(plan.requiresConfirmation)
    }

    @Test
    fun `ordinary slash command is not treated as moderation`() {
        val plan = CustomCommandSafety.executionPlan("/me waves")

        assertEquals(CustomCommandRisk.CHAT_COMMAND, plan.risk)
        assertFalse(plan.requiresPreview)
        assertFalse(plan.requiresConfirmation)
    }

    @Test
    fun `moderation macro requires confirmation`() {
        val plan = CustomCommandSafety.executionPlan("/timeout viewer 600 spam")

        assertEquals(CustomCommandRisk.MODERATION, plan.risk)
        assertFalse(plan.requiresPreview)
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `mass moderation macro requires preview and confirmation`() {
        val plan = CustomCommandSafety.executionPlan("/nuke scam phrase")

        assertEquals(CustomCommandRisk.MASS_MODERATION, plan.risk)
        assertTrue(plan.requiresPreview)
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `planner combines positional and runtime expansion with safety`() {
        val command = CustomCommand(
            name = "spam",
            template = "/timeout {user} {1} {2+}",
        )
        val context = CustomCommandRuntimeContext(
            base = baseContext,
            selectedUser = CustomCommandUser("user-id", "viewer"),
        )

        val result = CustomCommandPlanner.plan(
            command = command,
            arguments = listOf("600", "repeated", "spam"),
            context = context,
        ) as CustomCommandPlanResult.Success

        assertEquals("/timeout viewer 600 repeated spam", result.plan.expandedText)
        assertEquals(CustomCommandRisk.MODERATION, result.plan.risk)
        assertTrue(result.plan.requiresConfirmation)
    }
}
