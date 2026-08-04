package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPresentationPolicyTest {
    @Test
    fun `deleted message keeps original text when enabled`() {
        val message = message(isDeleted = true)

        assertEquals(
            "исходный текст",
            ChatPresentationPolicy.visibleText(message, showDeletedMessageContent = true),
        )
    }

    @Test
    fun `deleted message becomes placeholder when disabled`() {
        val message = message(isDeleted = true)

        assertEquals(
            ChatPresentationPolicy.DELETED_MESSAGE_PLACEHOLDER,
            ChatPresentationPolicy.visibleText(message, showDeletedMessageContent = false),
        )
    }

    @Test
    fun `moderator actions require moderator role and real session`() {
        assertTrue(ChatPresentationPolicy.shouldShowModeratorActions(isAuthenticated = true, isModerator = true))
        assertFalse(ChatPresentationPolicy.shouldShowModeratorActions(isAuthenticated = true, isModerator = false))
        assertFalse(ChatPresentationPolicy.shouldShowModeratorActions(isAuthenticated = false, isModerator = true))
    }

    private fun message(isDeleted: Boolean): ChatMessage = ChatMessage(
        id = "message-1",
        channelId = "channel-1",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-1",
            login = "viewer",
            displayName = "Viewer",
        ),
        text = "исходный текст",
        timestamp = "2026-07-21T18:00:00Z",
        flags = MessageFlags(isDeleted = isDeleted),
    )
}
