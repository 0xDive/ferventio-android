package io.ferventio.shared.ui.user

import io.ferventio.app.domain.ChannelUserRole
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedUserCardProjectionTest {
    @Test
    fun projectsStrongestRoleAndLatestProfileImage() {
        val source = message(
            id = "source",
            badges = listOf(ChatBadge(setId = "subscriber", id = "1")),
        )
        val recent = listOf(
            message(
                id = "older",
                badges = listOf(ChatBadge(setId = "moderator", id = "1")),
                profileImageUrl = "https://example.test/avatar.png",
            ),
            source,
        )

        val data = projectLocalUserCard(
            sourceMessage = source,
            channelMessages = recent,
            canModerate = true,
        )

        assertEquals(ChannelUserRole.MODERATOR, data.role)
        assertEquals("https://example.test/avatar.png", data.user.profileImageUrl)
        assertTrue(data.canModerate)
        assertEquals("source", data.sourceMessageId)
    }

    @Test
    fun limitsHistoryAndKeepsSourceWhenItIsNotInTimeline() {
        val source = message(id = "source")
        val history = (1..25).map { index -> message(id = "m$index") }

        val data = projectLocalUserCard(
            sourceMessage = source,
            channelMessages = history,
            canModerate = false,
        )

        assertEquals(USER_CARD_RECENT_MESSAGE_LIMIT, data.recentMessages.size)
        assertEquals("source", data.recentMessages.last().id)
        assertEquals(ChannelUserRole.VIEWER, data.role)
    }

    @Test
    fun blankIdsMatchByLoginIgnoringCase() {
        val source = message(id = "source", userId = "", login = "Viewer")
        val sameUser = message(id = "same", userId = "", login = "viewer")
        val other = message(id = "other", userId = "", login = "someone-else")

        val data = projectLocalUserCard(
            sourceMessage = source,
            channelMessages = listOf(sameUser, other),
            canModerate = false,
        )

        assertEquals(listOf("same", "source"), data.recentMessages.map(ChatMessage::id))
    }

    private fun message(
        id: String,
        userId: String = "user-id",
        login: String = "viewer",
        badges: List<ChatBadge> = emptyList(),
        profileImageUrl: String? = null,
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel-id",
        channelLogin = "channel",
        author = ChatAuthor(
            id = userId,
            login = login,
            displayName = "Viewer",
            badges = badges,
            profileImageUrl = profileImageUrl,
        ),
        text = "message $id",
        timestamp = "2026-08-17T12:00:00Z",
    )
}
