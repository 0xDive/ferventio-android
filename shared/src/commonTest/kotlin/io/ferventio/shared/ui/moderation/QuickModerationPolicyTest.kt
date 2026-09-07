package io.ferventio.shared.ui.moderation

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.MessageFlags
import io.ferventio.shared.settings.SharedLocalUiPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickModerationPolicyTest {
    private val enabled = SharedLocalUiPreferences(
        showQuickBan = true,
        showQuickDelete = true,
        confirmModerationActions = true,
    )

    @Test
    fun moderatorCanBanAndDeleteNormalOtherUserMessage() {
        val availability = quickModerationAvailability(
            message = message(),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = enabled,
        )

        assertTrue(availability.canBan)
        assertTrue(availability.canDelete)
    }

    @Test
    fun banExcludesSelfAndBroadcaster() {
        val self = quickModerationAvailability(
            message = message(userId = "viewer-id"),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = enabled,
        )
        val broadcaster = quickModerationAvailability(
            message = message(
                badges = listOf(ChatBadge(setId = "broadcaster", id = "1")),
            ),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = enabled,
        )

        assertFalse(self.canBan)
        assertTrue(self.canDelete)
        assertFalse(broadcaster.canBan)
        assertTrue(broadcaster.canDelete)
    }

    @Test
    fun moderatorContextAndMessageStateGateActions() {
        val noModeratorContext = quickModerationAvailability(
            message = message(),
            ownUserId = "viewer-id",
            canModerate = false,
            preferences = enabled,
        )
        val deleted = quickModerationAvailability(
            message = message(flags = MessageFlags(isDeleted = true)),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = enabled,
        )
        val system = quickModerationAvailability(
            message = message(type = ChatMessageType.SYSTEM),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = enabled,
        )

        assertFalse(noModeratorContext.canBan)
        assertFalse(noModeratorContext.canDelete)
        assertFalse(deleted.canBan)
        assertFalse(deleted.canDelete)
        assertFalse(system.canBan)
        assertFalse(system.canDelete)
    }

    @Test
    fun disabledPreferencesHideActions() {
        val availability = quickModerationAvailability(
            message = message(),
            ownUserId = "viewer-id",
            canModerate = true,
            preferences = SharedLocalUiPreferences(),
        )

        assertFalse(availability.canBan)
        assertFalse(availability.canDelete)
    }

    private fun message(
        userId: String = "author-id",
        badges: List<ChatBadge> = emptyList(),
        flags: MessageFlags = MessageFlags(),
        type: ChatMessageType = ChatMessageType.CHAT,
    ) = ChatMessage(
        id = "message-id",
        channelId = "channel-id",
        channelLogin = "channel",
        author = ChatAuthor(
            id = userId,
            login = "author",
            displayName = "Author",
            badges = badges,
        ),
        text = "hello",
        timestamp = "2026-08-20T16:00:00Z",
        timestampMillis = 1_000L,
        flags = flags,
        type = type,
    )
}
