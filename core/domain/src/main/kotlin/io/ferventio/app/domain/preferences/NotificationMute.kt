package io.ferventio.app.domain

enum class NotificationMuteRemainingUnit {
    MINUTES,
    HOURS,
    DAYS,
}

data class NotificationMuteRemaining(
    val value: Long,
    val unit: NotificationMuteRemainingUnit,
)

fun notificationMuteRemaining(
    mutedUntilEpochMillis: Long?,
    nowEpochMillis: Long,
): NotificationMuteRemaining? {
    val mutedUntil = mutedUntilEpochMillis ?: return null
    val remainingMillis = mutedUntil - nowEpochMillis
    if (remainingMillis <= 0L) return null

    val minutes = ceilPositiveDivision(remainingMillis, MILLIS_PER_MINUTE)
    return when {
        minutes >= MINUTES_PER_DAY -> NotificationMuteRemaining(
            value = ceilPositiveDivision(minutes, MINUTES_PER_DAY),
            unit = NotificationMuteRemainingUnit.DAYS,
        )
        minutes >= MINUTES_PER_HOUR -> NotificationMuteRemaining(
            value = ceilPositiveDivision(minutes, MINUTES_PER_HOUR),
            unit = NotificationMuteRemainingUnit.HOURS,
        )
        else -> NotificationMuteRemaining(
            value = minutes,
            unit = NotificationMuteRemainingUnit.MINUTES,
        )
    }
}

private fun ceilPositiveDivision(value: Long, divisor: Long): Long =
    value / divisor + if (value % divisor == 0L) 0L else 1L

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
