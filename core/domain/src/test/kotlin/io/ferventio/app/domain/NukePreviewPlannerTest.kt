package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NukePreviewPlannerTest {
    private val now = 100_000L

    @Test
    fun `plain text preview counts messages and unique users`() {
        val messages = listOf(
            message("1", "alpha", "free skins now", 90_000L),
            message("2", "alpha", "FREE SKINS", 91_000L),
            message("3", "beta", "get free skins", 92_000L),
            message("4", "gamma", "normal chat", 93_000L),
        )

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(query = "free skins"),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(3, result.preview.matchedMessageCount)
        assertEquals(2, result.preview.matchedUserCount)
        assertEquals(setOf("alpha", "beta"), result.preview.matchedUserIds)
        assertEquals(listOf("1", "2", "3"), result.preview.matchedMessageIds)
        assertEquals(listOf("alpha", "beta"), result.preview.matchedUsers.map(NukeTargetUser::userLogin))
    }

    @Test
    fun `protected roles are excluded by default`() {
        val messages = listOf(
            message("1", "mod", "spam phrase", 90_000L, listOf(ChatBadge("moderator", "1"))),
            message("2", "vip", "spam phrase", 91_000L, listOf(ChatBadge("vip", "1"))),
            message("3", "viewer", "spam phrase", 92_000L),
        )

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(query = "spam phrase"),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(1, result.preview.matchedMessageCount)
        assertEquals(2, result.preview.excludedMatchCount)
        assertEquals(setOf("viewer"), result.preview.matchedUserIds)
    }

    @Test
    fun `window limits scan to recent messages`() {
        val messages = listOf(
            message("1", "old", "spam", 60_000L),
            message("2", "recent", "spam", 95_000L),
        )

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(query = "spam", windowMillis = 10_000L),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(1, result.preview.scannedMessageCount)
        assertEquals(setOf("recent"), result.preview.matchedUserIds)
    }

    @Test
    fun `regex mode supports case insensitive matching`() {
        val messages = listOf(
            message("1", "alpha", "visit SCAM123 now", 95_000L),
            message("2", "beta", "scam999", 96_000L),
        )

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(
                query = "scam\\d+",
                matchMode = NukeMatchMode.REGEX,
            ),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(2, result.preview.matchedMessageCount)
    }

    @Test
    fun `invalid regex returns error instead of throwing`() {
        val result = NukePreviewPlanner.build(
            messages = emptyList(),
            config = NukePreviewConfig(query = "[", matchMode = NukeMatchMode.REGEX),
            nowMillis = now,
        )

        assertTrue(result is NukePreviewResult.Error)
    }

    @Test
    fun `samples are bounded independently from frozen target set`() {
        val messages = (1..5).map { index ->
            message(index.toString(), "user$index", "spam", 90_000L + index)
        }

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(query = "spam", maxSamples = 2),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(5, result.preview.matchedMessageCount)
        assertEquals(2, result.preview.samples.size)
        assertEquals(5, result.preview.matchedMessageIds.size)
        assertEquals(5, result.preview.matchedUsers.size)
    }

    @Test
    fun `explicit user exclusion participates in preview counts`() {
        val messages = listOf(
            message("1", "alpha", "spam", 95_000L),
            message("2", "beta", "spam", 96_000L),
        )

        val result = NukePreviewPlanner.build(
            messages = messages,
            config = NukePreviewConfig(query = "spam", excludedUserIds = setOf("beta")),
            nowMillis = now,
        ) as NukePreviewResult.Success

        assertEquals(1, result.preview.matchedMessageCount)
        assertEquals(1, result.preview.excludedMatchCount)
        assertEquals(setOf("alpha"), result.preview.matchedUserIds)
    }

    @Test
    fun `execution plan freezes exact preview targets`() {
        val config = NukePreviewConfig(query = "spam")
        val preview = (
            NukePreviewPlanner.build(
                messages = listOf(
                    message("1", "alpha", "spam", 95_000L),
                    message("2", "beta", "spam", 96_000L),
                ),
                config = config,
                nowMillis = now,
            ) as NukePreviewResult.Success
            ).preview

        val frozen = NukeExecutionPlanner.freeze(config, preview, previewedAtMillis = now)
            as NukeExecutionPlanResult.Success

        assertEquals(listOf("alpha", "beta"), frozen.plan.targetUsers.map(NukeTargetUser::userLogin))
        assertEquals(listOf("1", "2"), frozen.plan.targetMessageIds)
        assertEquals(2, frozen.plan.targetUserCount)
        assertEquals(2, frozen.plan.targetMessageCount)
        assertEquals(now, frozen.plan.previewedAtMillis)
    }

    @Test
    fun `execution plan rejects empty target set`() {
        val preview = (
            NukePreviewPlanner.build(
                messages = listOf(message("1", "alpha", "normal", 95_000L)),
                config = NukePreviewConfig(query = "spam"),
                nowMillis = now,
            ) as NukePreviewResult.Success
            ).preview

        val frozen = NukeExecutionPlanner.freeze(
            config = NukePreviewConfig(query = "spam"),
            preview = preview,
            previewedAtMillis = now,
        )

        assertTrue(frozen is NukeExecutionPlanResult.Error)
    }

    private fun message(
        id: String,
        user: String,
        text: String,
        timestampMillis: Long,
        badges: List<ChatBadge> = emptyList(),
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = user,
            login = user,
            displayName = user,
            badges = badges,
        ),
        text = text,
        timestamp = "2026-08-07T00:00:00Z",
        timestampMillis = timestampMillis,
    )
}
