package io.ferventio.shared.ui.user

import io.ferventio.app.domain.ChannelUserRole
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.UserCardData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedUserCardProjectionTest {
    @Test
    fun recentMessageMergeUpdatesLiveCopiesInOnePass() {
        val cached = listOf(
            message(id = "one").copy(text = "old"),
            message(id = "two"),
        )
        val live = listOf(
            message(id = "unrelated"),
            message(id = "one").copy(text = "new"),
        )

        val merged = mergeUserCardRecentMessagesWithLive(cached, live)

        assertEquals("new", merged.first().text)
        assertEquals("two", merged.last().id)
        assertFalse(merged === cached)
    }

    @Test
    fun recentMessageMergePreservesListIdentityWhenNothingChanged() {
        val cached = listOf(message(id = "one"), message(id = "two"))
        val live = listOf(message(id = "unrelated"))

        val merged = mergeUserCardRecentMessagesWithLive(cached, live)

        assertTrue(merged === cached)
    }

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
    fun projectsModerationUserWithLocalHistoryAndGroupFallback() {
        val recent = message(
            id = "recent",
            userId = "mod-id",
            login = "moderator",
            profileImageUrl = "https://example.test/mod.png",
        )
        val data = projectModerationUserCard(
            channel = ChatChannel(
                id = "channel-id",
                login = "channel",
                displayName = "Channel",
            ),
            user = ModerationUser(
                id = "mod-id",
                login = "moderator",
                displayName = "Moderator",
                group = ModerationUserGroup.MODERATOR,
            ),
            channelMessages = listOf(recent),
            canModerate = true,
        )

        assertEquals(ChannelUserRole.MODERATOR, data.role)
        assertEquals("https://example.test/mod.png", data.user.profileImageUrl)
        assertEquals(listOf("recent"), data.recentMessages.map(ChatMessage::id))
        assertTrue(data.canModerate)
    }

    @Test
    fun broadcasterGroupSurvivesWithoutLocalBadges() {
        val data = projectModerationUserCard(
            channel = ChatChannel(
                id = "channel-id",
                login = "channel",
                displayName = "Channel",
            ),
            user = ModerationUser(
                id = "channel-id",
                login = "channel",
                displayName = "Channel",
                group = ModerationUserGroup.BROADCASTER,
            ),
            channelMessages = emptyList(),
            canModerate = true,
        )

        assertEquals(ChannelUserRole.BROADCASTER, data.role)
        assertTrue(data.recentMessages.isEmpty())
    }

    @Test
    fun mobileRecentMessagesKeepNewestFirstAndStayBounded() {
        val messages = (1..20).map { index -> message(id = "m$index") }

        val visible = userCardRecentMessagesForDisplay(
            messages = messages,
            selectedMessageId = null,
        )

        assertEquals(12, visible.size)
        assertEquals("m20", visible.first().id)
        assertEquals("m9", visible.last().id)
    }

    @Test
    fun mobileRecentMessagesKeepOlderSelectedMessageVisible() {
        val messages = (1..20).map { index -> message(id = "m$index") }

        val visible = userCardRecentMessagesForDisplay(
            messages = messages,
            selectedMessageId = "m3",
        )

        assertEquals(12, visible.size)
        assertEquals("m3", visible.first().id)
        assertTrue(visible.any { it.id == "m20" })
    }

    @Test
    fun mentionDraftAppendsNormalizedLoginWithoutDroppingExistingText() {
        assertEquals(
            "hello @viewer ",
            appendUserMentionDraft(
                currentDraft = "hello ",
                userLogin = "@viewer",
            ),
        )
        assertEquals(
            "@viewer ",
            appendUserMentionDraft(
                currentDraft = "",
                userLogin = "viewer",
            ),
        )
    }

    @Test
    fun blankMentionLoginLeavesDraftUnchanged() {
        assertEquals(
            "hello",
            appendUserMentionDraft(
                currentDraft = "hello",
                userLogin = "   ",
            ),
        )
    }

    @Test
    fun userCardBlockAvailabilityRejectsSelfAndMissingIdentity() {
        val target = UserCardData(
            channelId = "channel-id",
            user = TwitchUser(
                id = "target-id",
                login = "target",
                displayName = "Target",
            ),
        )

        assertTrue(canBlockUserCardUser(target, authenticatedUserId = "viewer-id"))
        assertFalse(canBlockUserCardUser(target, authenticatedUserId = "target-id"))
        assertFalse(canBlockUserCardUser(target, authenticatedUserId = null))
        assertFalse(
            canBlockUserCardUser(
                target.copy(user = target.user.copy(id = "")),
                authenticatedUserId = "viewer-id",
            ),
        )
    }

    @Test
    fun profileDatePresentationDropsRawIsoTimeSuffix() {
        assertEquals(
            "2023-12-01",
            formatUserCardProfileDate("2023-12-01T18:45:00Z"),
        )
        assertEquals(
            "unknown",
            formatUserCardProfileDate(" unknown "),
        )
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
