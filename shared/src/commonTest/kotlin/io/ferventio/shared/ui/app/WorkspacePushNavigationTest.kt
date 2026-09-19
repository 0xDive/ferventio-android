package io.ferventio.shared.ui.app

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.push.PushChannelReference
import io.ferventio.shared.push.PushNavigationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspacePushNavigationTest {
    private val channels = listOf(
        ChatChannel(id = "1", login = "alpha", displayName = "Alpha"),
        ChatChannel(id = "2", login = "Beta", displayName = "Beta"),
    )

    @Test
    fun pushSettingsDoesNotRequireWorkspaceChannel() {
        assertEquals(
            WorkspacePushNavigationAction.OpenSettings,
            resolveWorkspacePushNavigationAction(PushNavigationTarget.PushSettings, emptyList()),
        )
    }

    @Test
    fun channelIdWinsWhenReferenceAlsoContainsDifferentLogin() {
        assertEquals(
            WorkspacePushNavigationAction.SelectChannel("1"),
            resolveWorkspacePushNavigationAction(
                target = PushNavigationTarget.Channel(
                    PushChannelReference(id = "1", login = "beta"),
                ),
                channels = channels,
            ),
        )
    }

    @Test
    fun channelLoginProvidesCaseInsensitiveFallback() {
        assertEquals(
            WorkspacePushNavigationAction.OpenMessage("2", "message-1"),
            resolveWorkspacePushNavigationAction(
                target = PushNavigationTarget.Message(
                    channel = PushChannelReference(login = " bEtA "),
                    messageId = "message-1",
                ),
                channels = channels,
            ),
        )
    }

    @Test
    fun mentionsAndModerationRequireKnownWorkspaceChannel() {
        assertEquals(
            WorkspacePushNavigationAction.OpenMentions("1"),
            resolveWorkspacePushNavigationAction(
                PushNavigationTarget.Mentions(PushChannelReference(id = "1")),
                channels,
            ),
        )
        assertEquals(
            WorkspacePushNavigationAction.OpenModeration("2"),
            resolveWorkspacePushNavigationAction(
                PushNavigationTarget.Moderation(PushChannelReference(login = "beta")),
                channels,
            ),
        )
        assertNull(
            resolveWorkspacePushNavigationAction(
                PushNavigationTarget.Channel(PushChannelReference(id = "missing")),
                channels,
            ),
        )
    }
}
