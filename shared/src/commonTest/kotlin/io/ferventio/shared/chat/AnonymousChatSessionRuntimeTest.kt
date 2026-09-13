package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.ModerationAction
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymousChatSessionRuntimeTest {
    @Test
    fun messageEventsFeedSharedTimelineAndUnreadState() {
        val state = ChatRuntimeStateHolder()
        val attention = ChatAttentionStateHolder()
        val workspace = workspace()
        val runtime = AnonymousChatSessionRuntime(state, attention, workspace)
        val message = message("m1", channelId = PLACEHOLDER_ID)

        assertTrue(runtime.onEvent(ChatEvent.Message(message)))

        assertEquals(listOf("m1"), state.messages(PLACEHOLDER_ID).map(ChatMessage::id))
        assertEquals(1, attention.attention(PLACEHOLDER_ID).unreadCount)
    }

    @Test
    fun ircModerationEventsUseSharedDeletionSemantics() {
        val state = ChatRuntimeStateHolder()
        val attention = ChatAttentionStateHolder()
        val workspace = workspace()
        val runtime = AnonymousChatSessionRuntime(state, attention, workspace)
        runtime.onEvent(ChatEvent.Message(message("a", PLACEHOLDER_ID, authorId = "viewer")))
        runtime.onEvent(ChatEvent.Message(message("b", PLACEHOLDER_ID, authorId = "viewer")))

        assertTrue(runtime.onEvent(ChatEvent.MessageDeleted(PLACEHOLDER_ID, "a")))
        assertEquals(
            ModerationAction.DELETE,
            state.messages(PLACEHOLDER_ID).first { it.id == "a" }.moderation.action,
        )

        assertTrue(
            runtime.onEvent(
                ChatEvent.UserMessagesCleared(
                    channelId = PLACEHOLDER_ID,
                    userId = "viewer",
                    durationSeconds = 600,
                    isPermanent = false,
                ),
            ),
        )
        assertEquals(
            ModerationAction.TIMEOUT,
            state.messages(PLACEHOLDER_ID).first { it.id == "b" }.moderation.action,
        )

        assertTrue(
            runtime.onEvent(
                ChatEvent.UserMessagesCleared(
                    channelId = PLACEHOLDER_ID,
                    userId = "viewer",
                    isPermanent = true,
                ),
            ),
        )
        assertEquals(
            ModerationAction.BAN,
            state.messages(PLACEHOLDER_ID).first { it.id == "b" }.moderation.action,
        )

        assertTrue(runtime.onEvent(ChatEvent.ChatCleared(PLACEHOLDER_ID)))
        assertEquals(emptyList(), state.messages(PLACEHOLDER_ID))
    }

    @Test
    fun unsupportedAuthenticatedOnlyEventsAreIgnored() {
        val runtime = AnonymousChatSessionRuntime(
            state = ChatRuntimeStateHolder(),
            attention = ChatAttentionStateHolder(),
            workspace = workspace(),
        )

        assertFalse(
            runtime.onEvent(
                ChatEvent.ChatSettingsUpdated(
                    io.ferventio.app.domain.ModerationChatSettings(channelId = PLACEHOLDER_ID),
                ),
            ),
        )
    }

    @Test
    fun roomResolutionRemapsWorkspaceBeforeRealIdMessagesArrive() {
        val state = ChatRuntimeStateHolder()
        val attention = ChatAttentionStateHolder()
        val workspace = workspace()
        val runtime = AnonymousChatSessionRuntime(state, attention, workspace)

        runtime.onRoomResolved("alpha", "1234")
        runtime.onEvent(ChatEvent.Message(message("real", channelId = "1234")))

        assertEquals(listOf("1234"), workspace.channelIds)
        assertEquals("1234", workspace.selectedChannelId)
        assertEquals(listOf("real"), state.messages("1234").map(ChatMessage::id))
        assertEquals(1, attention.attention("1234").unreadCount)
    }

    @Test
    fun roomResolutionPreservesPlaceholderMessagesAndAttention() {
        val state = ChatRuntimeStateHolder()
        val attention = ChatAttentionStateHolder()
        val workspace = workspace()
        val runtime = AnonymousChatSessionRuntime(state, attention, workspace)
        runtime.onEvent(ChatEvent.Message(message("early", channelId = PLACEHOLDER_ID)))
        attention.requestMessageNavigation(PLACEHOLDER_ID, "early")

        runtime.onRoomResolved("alpha", "1234")

        assertEquals(emptyList(), state.messages(PLACEHOLDER_ID))
        assertEquals(listOf("early"), state.messages("1234").map(ChatMessage::id))
        assertEquals("1234", state.messages("1234").single().channelId)
        assertEquals(1, attention.attention("1234").unreadCount)
        assertEquals(0, attention.attention(PLACEHOLDER_ID).unreadCount)
        assertEquals("early", attention.navigationTarget("1234"))
    }

    @Test
    fun connectionAndNoticeStateStayInSharedHolder() {
        val state = ChatRuntimeStateHolder()
        val runtime = AnonymousChatSessionRuntime(
            state = state,
            attention = ChatAttentionStateHolder(),
            workspace = workspace(),
        )

        runtime.onConnectionUpdate(
            TwitchAnonymousChatConnectionUpdate(
                status = ConnectionStatus.RECONNECTING,
                stage = TwitchAnonymousChatConnectionStage.RETRY_WAIT,
                attempt = 2,
                error = "network",
            ),
        )
        runtime.onNotice(" read only notice ")

        assertEquals(ConnectionStatus.RECONNECTING, state.connectionStatus)
        assertEquals(2, state.connectionAttempt)
        assertEquals("network", state.connectionErrorMessage)
        assertEquals("read only notice", state.connectionDetail)
    }

    private fun workspace() = WorkspaceRuntimeStateHolder(
        WorkspaceRuntimeSnapshot(
            channels = listOf(
                ChatChannel(
                    id = PLACEHOLDER_ID,
                    login = "alpha",
                    displayName = "alpha",
                ),
            ),
            selectedChannelId = PLACEHOLDER_ID,
        ),
    )

    private fun message(
        id: String,
        channelId: String,
        authorId: String = "author",
    ) = ChatMessage(
        id = id,
        channelId = channelId,
        channelLogin = "alpha",
        author = ChatAuthor(
            id = authorId,
            login = authorId,
            displayName = authorId,
        ),
        text = id,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = 1L,
    )

    private companion object {
        const val PLACEHOLDER_ID = "anonymous:alpha"
    }
}
