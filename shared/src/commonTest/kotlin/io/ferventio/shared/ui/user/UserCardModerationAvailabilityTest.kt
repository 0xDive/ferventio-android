package io.ferventio.shared.ui.user

import io.ferventio.app.domain.ChannelUserRole
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.UserCardData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserCardModerationAvailabilityTest {
    @Test
    fun moderatorCanTargetOtherViewerAndDeleteSelectedMessage() {
        val availability = userCardModerationAvailability(
            data = data(role = ChannelUserRole.VIEWER),
            authenticatedUserId = "moderator",
        )

        assertTrue(availability.canModerateUser)
        assertTrue(availability.canDeleteSourceMessage)
    }

    @Test
    fun selfAndBroadcasterCannotBeTimeoutOrBanTargets() {
        assertFalse(
            userCardModerationAvailability(
                data = data(role = ChannelUserRole.VIEWER, targetUserId = "moderator"),
                authenticatedUserId = "moderator",
            ).canModerateUser,
        )
        assertFalse(
            userCardModerationAvailability(
                data = data(role = ChannelUserRole.BROADCASTER),
                authenticatedUserId = "moderator",
            ).canModerateUser,
        )
    }

    @Test
    fun missingModeratorContextOrDeletedSourceDisablesUnsafeActions() {
        val noContext = data(role = ChannelUserRole.VIEWER).copy(canModerate = false)
        val deleted = data(
            role = ChannelUserRole.VIEWER,
            sourceFlags = MessageFlags(isDeleted = true),
        )

        assertFalse(
            userCardModerationAvailability(noContext, "moderator").canModerateUser,
        )
        assertFalse(
            userCardModerationAvailability(noContext, "moderator").canDeleteSourceMessage,
        )
        assertFalse(
            userCardModerationAvailability(deleted, "moderator").canDeleteSourceMessage,
        )
    }

    private fun data(
        role: ChannelUserRole,
        targetUserId: String = "viewer",
        sourceFlags: MessageFlags = MessageFlags(),
    ): UserCardData {
        val source = ChatMessage(
            id = "message",
            channelId = "channel",
            channelLogin = "channel",
            author = ChatAuthor(
                id = targetUserId,
                login = "viewer",
                displayName = "Viewer",
            ),
            text = "hello",
            timestamp = "2026-08-17T00:00:00Z",
            timestampMillis = 0L,
            flags = sourceFlags,
        )
        return UserCardData(
            channelId = "channel",
            user = TwitchUser(
                id = targetUserId,
                login = "viewer",
                displayName = "Viewer",
            ),
            role = role,
            canModerate = true,
            sourceMessageId = source.id,
            recentMessages = listOf(source),
        )
    }
}
