package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserCardContextBuilderTest {
    private val now = 1_785_960_000_000L // 2026-08-07T12:00:00Z-ish fixed test clock

    @Test
    fun `derives account follow activity and moderation facts`() {
        val data = UserCardData(
            channelId = "channel",
            user = TwitchUser(
                id = "user",
                login = "viewer",
                displayName = "Viewer",
                createdAt = "2026-08-01T12:00:00Z",
            ),
            followerInfo = ChannelFollowerInfo(followedAt = "2026-08-05T12:00:00Z"),
            recentMessages = listOf(
                message("1", 10_000L),
                message("2", 12_000L, deleted = true),
                message("3", 15_000L),
            ),
            localActions = listOf(
                action("1", "timeout", 20_000L),
                action("2", "warn", 21_000L),
                action("3", "ban", 22_000L),
            ),
        )

        val summary = UserCardContextBuilder.build(data, now)

        assertEquals(3, summary.recentMessageCount)
        assertEquals(5_000L, summary.recentMessageWindowMillis)
        assertEquals(1, summary.deletedRecentMessageCount)
        assertEquals(3, summary.moderationActionCount)
        assertEquals(1, summary.timeoutCount)
        assertEquals(1, summary.warningCount)
        assertEquals(1, summary.banCount)
        assertEquals("3", summary.lastModerationAction?.id)
    }

    @Test
    fun `invalid timestamps stay absent instead of throwing`() {
        val data = UserCardData(
            channelId = "channel",
            user = TwitchUser(
                id = "user",
                login = "viewer",
                displayName = "Viewer",
                createdAt = "not-an-instant",
            ),
            followerInfo = ChannelFollowerInfo(followedAt = "also-invalid"),
        )

        val summary = UserCardContextBuilder.build(data, now)

        assertNull(summary.accountCreatedAtMillis)
        assertNull(summary.accountAgeDays)
        assertNull(summary.followedAtMillis)
        assertNull(summary.followAgeDays)
    }

    @Test
    fun `future timestamps do not create negative ages`() {
        val data = UserCardData(
            channelId = "channel",
            user = TwitchUser(
                id = "user",
                login = "viewer",
                displayName = "Viewer",
                createdAt = "2099-01-01T00:00:00Z",
            ),
        )

        val summary = UserCardContextBuilder.build(data, now)

        assertNull(summary.accountAgeDays)
    }

    private fun message(id: String, timestampMillis: Long, deleted: Boolean = false): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(id = "user", login = "viewer", displayName = "Viewer"),
        text = "message",
        timestamp = "2026-08-07T00:00:00Z",
        timestampMillis = timestampMillis,
        flags = MessageFlags(isDeleted = deleted),
    )

    private fun action(id: String, action: String, createdAtMillis: Long): LocalModerationAction =
        LocalModerationAction(
            id = id,
            channelId = "channel",
            targetUserId = "user",
            targetUserLogin = "viewer",
            messageId = null,
            action = action,
            durationSeconds = null,
            reason = null,
            createdAtMillis = createdAtMillis,
        )
}
