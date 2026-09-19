package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ConfirmedModerationCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedComposerCommandRoutingTest {
    @Test
    fun plainAndUnknownBotCommandsPassThrough() {
        assertEquals(
            SharedComposerSubmission.Send("hello"),
            routeSharedComposerSubmission(" hello "),
        )
        assertEquals(
            SharedComposerSubmission.Send("/nightbot song"),
            routeSharedComposerSubmission("/nightbot song"),
        )
    }

    @Test
    fun actionCommandNormalizesAndStaysWireCompatible() {
        assertEquals(
            SharedComposerSubmission.Send("/me waves"),
            routeSharedComposerSubmission("/me   waves"),
        )
    }

    @Test
    fun userCardCommandRoutesWithoutSendingText() {
        assertEquals(
            SharedComposerSubmission.UserCard("some_user"),
            routeSharedComposerSubmission("/user @some_user"),
        )
    }

    @Test
    fun moderationCommandsRouteToDomainCommand() {
        val ban = assertIs<SharedComposerSubmission.Moderation>(
            routeSharedComposerSubmission("/ban trouble spam"),
        )
        assertEquals(
            ConfirmedModerationCommand.Ban("trouble", "spam"),
            ban.command,
        )

        val slow = assertIs<SharedComposerSubmission.Moderation>(
            routeSharedComposerSubmission("/slow 10"),
        )
        assertEquals(ConfirmedModerationCommand.Slow(10), slow.command)
    }

    @Test
    fun malformedKnownCommandReturnsParserError() {
        assertIs<SharedComposerSubmission.Error>(
            routeSharedComposerSubmission("/timeout"),
        )
    }
    @Test
    fun nukeCommandRoutesToPreviewInsteadOfChatWire() {
        val routed = assertIs<SharedComposerSubmission.Nuke>(
            routeSharedComposerSubmission("/nuke spam phrase"),
        )
        assertEquals("spam phrase", routed.config.query)
    }

    @Test
    fun emptyNukeCommandReturnsParserError() {
        assertIs<SharedComposerSubmission.Error>(
            routeSharedComposerSubmission("/nuke"),
        )
    }

}
