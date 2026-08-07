package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCommandComposerResolverTest {
    private val baseContext = CustomCommandContext(
        channelName = "Streamer",
        channelId = "channel-id",
        myName = "me",
        myId = "my-id",
    )

    private fun context(
        reply: CustomCommandReply? = null,
        clipboardText: String? = null,
    ) = CustomCommandRuntimeContext(
        base = baseContext,
        selectedUser = reply?.user,
        reply = reply,
        clipboardText = clipboardText,
    )

    @Test
    fun `unknown slash command passes through exactly`() {
        val raw = "  /SomeBot   \"unterminated"

        val result = CustomCommandComposerResolver.resolve(
            input = raw,
            commands = listOf(CustomCommand("hello", "Hello chat")),
            context = context(),
        )

        assertEquals(CustomCommandComposerResolution.PassThrough(raw), result)
    }

    @Test
    fun `disabled custom command passes through`() {
        val raw = "/hello viewer"

        val result = CustomCommandComposerResolver.resolve(
            input = raw,
            commands = listOf(CustomCommand("hello", "Hello {1}", enabled = false)),
            context = context(),
        )

        assertEquals(CustomCommandComposerResolution.PassThrough(raw), result)
    }

    @Test
    fun `enabled safe custom command expands positional arguments`() {
        val result = CustomCommandComposerResolver.resolve(
            input = "/hello \"Chat Friend\"",
            commands = listOf(CustomCommand("hello", "Hello {1}!")),
            context = context(),
        ) as CustomCommandComposerResolution.Planned

        assertEquals("Hello Chat Friend!", result.plan.expandedText)
        assertEquals(CustomCommandRisk.SAFE_TEXT, result.plan.risk)
    }

    @Test
    fun `reply and clipboard runtime variables expand at composer boundary`() {
        val reply = CustomCommandReply(
            messageId = "message-id",
            user = CustomCommandUser("viewer-id", "viewer", "Viewer"),
            text = "original message",
        )

        val result = CustomCommandComposerResolver.resolve(
            input = "/context",
            commands = listOf(
                CustomCommand(
                    "context",
                    "Replying to {reply.user}: {reply.text}; user={user}; clip={clipboard}",
                ),
            ),
            context = context(reply = reply, clipboardText = "copied"),
        ) as CustomCommandComposerResolution.Planned

        assertEquals(
            "Replying to viewer: original message; user=viewer; clip=copied",
            result.plan.expandedText,
        )
    }

    @Test
    fun `nested custom command resolves to final moderation risk`() {
        val commands = listOf(
            CustomCommand("mod", "/timeout {1} 600 repeated spam"),
            CustomCommand("punish", "/mod {1}"),
        )

        val result = CustomCommandComposerResolver.resolve(
            input = "/punish viewer",
            commands = commands,
            context = context(),
        ) as CustomCommandComposerResolution.Planned

        assertEquals("/timeout viewer 600 repeated spam", result.plan.expandedText)
        assertEquals(CustomCommandRisk.MODERATION, result.plan.risk)
        assertTrue(result.plan.requiresConfirmation)
    }

    @Test
    fun `nested mass moderation retains preview requirement`() {
        val commands = listOf(
            CustomCommand("cleanup", "/nuke {1+}"),
            CustomCommand("wave", "/cleanup {1+}"),
        )

        val result = CustomCommandComposerResolver.resolve(
            input = "/wave scam phrase",
            commands = commands,
            context = context(),
        ) as CustomCommandComposerResolution.Planned

        assertEquals("/nuke scam phrase", result.plan.expandedText)
        assertEquals(CustomCommandRisk.MASS_MODERATION, result.plan.risk)
        assertTrue(result.plan.requiresPreview)
        assertTrue(result.plan.requiresConfirmation)
    }

    @Test
    fun `cycle is rejected`() {
        val result = CustomCommandComposerResolver.resolve(
            input = "/one",
            commands = listOf(
                CustomCommand("one", "/two"),
                CustomCommand("two", "/one"),
            ),
            context = context(),
        )

        assertTrue(result is CustomCommandComposerResolution.Error)
        assertTrue((result as CustomCommandComposerResolution.Error).message.contains("cycle"))
    }

    @Test
    fun `maximum recursive depth is bounded`() {
        val commands = (1..9).map { index ->
            CustomCommand(
                name = "c$index",
                template = if (index == 9) "done" else "/c${index + 1}",
            )
        }

        val result = CustomCommandComposerResolver.resolve(
            input = "/c1",
            commands = commands,
            context = context(),
        )

        assertTrue(result is CustomCommandComposerResolution.Error)
        assertTrue((result as CustomCommandComposerResolution.Error).message.contains("maximum depth"))
    }

    @Test
    fun `missing runtime context is surfaced as an error`() {
        val result = CustomCommandComposerResolver.resolve(
            input = "/so",
            commands = listOf(CustomCommand("so", "Check out {user}")),
            context = context(),
        )

        assertTrue(result is CustomCommandComposerResolution.Error)
        assertTrue((result as CustomCommandComposerResolution.Error).message.contains("{user}"))
    }
}
