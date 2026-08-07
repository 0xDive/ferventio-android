package io.ferventio.app.domain

import java.time.Instant

data class UserCardContextSummary(
    val accountCreatedAtMillis: Long? = null,
    val accountAgeDays: Long? = null,
    val followedAtMillis: Long? = null,
    val followAgeDays: Long? = null,
    val recentMessageCount: Int = 0,
    val recentMessageWindowMillis: Long? = null,
    val deletedRecentMessageCount: Int = 0,
    val moderationActionCount: Int = 0,
    val timeoutCount: Int = 0,
    val banCount: Int = 0,
    val warningCount: Int = 0,
    val lastModerationAction: LocalModerationAction? = null,
)

/**
 * Derives compact, factual context for a richer user card from data Ferventio
 * already owns. This intentionally does not calculate a suspicion/risk score;
 * moderation decisions stay with the moderator.
 */
object UserCardContextBuilder {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun build(
        data: UserCardData,
        nowMillis: Long,
    ): UserCardContextSummary {
        val accountCreatedAt = parseInstantMillis(data.user.createdAt)
        val followedAt = parseInstantMillis(data.followerInfo.followedAt)
        val recentMessages = data.recentMessages.sortedBy(ChatMessage::timestampMillis)
        val recentWindow = when {
            recentMessages.size < 2 -> null
            else -> (recentMessages.last().timestampMillis - recentMessages.first().timestampMillis)
                .coerceAtLeast(0L)
        }
        val actions = data.localActions.sortedBy(LocalModerationAction::createdAtMillis)

        return UserCardContextSummary(
            accountCreatedAtMillis = accountCreatedAt,
            accountAgeDays = ageDays(accountCreatedAt, nowMillis),
            followedAtMillis = followedAt,
            followAgeDays = ageDays(followedAt, nowMillis),
            recentMessageCount = recentMessages.size,
            recentMessageWindowMillis = recentWindow,
            deletedRecentMessageCount = recentMessages.count(ChatMessage::isDeleted),
            moderationActionCount = actions.size,
            timeoutCount = actions.count { it.action.equals("timeout", ignoreCase = true) },
            banCount = actions.count { it.action.equals("ban", ignoreCase = true) },
            warningCount = actions.count {
                it.action.equals("warn", ignoreCase = true) ||
                    it.action.equals("warning", ignoreCase = true)
            },
            lastModerationAction = actions.lastOrNull(),
        )
    }

    private fun parseInstantMillis(value: String?): Long? = value
        ?.takeIf(String::isNotBlank)
        ?.let { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }

    private fun ageDays(startMillis: Long?, nowMillis: Long): Long? = startMillis
        ?.takeIf { it <= nowMillis }
        ?.let { start -> (nowMillis - start) / MILLIS_PER_DAY }
}
