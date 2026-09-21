package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationMuteTest {
    @Test
    fun expiredOrMissingMuteHasNoRemainingDuration() {
        assertNull(notificationMuteRemaining(null, nowEpochMillis = 1_000L))
        assertNull(notificationMuteRemaining(1_000L, nowEpochMillis = 1_000L))
        assertNull(notificationMuteRemaining(999L, nowEpochMillis = 1_000L))
    }

    @Test
    fun remainingDurationRoundsUpToUsefulDisplayUnit() {
        assertEquals(
            NotificationMuteRemaining(1L, NotificationMuteRemainingUnit.MINUTES),
            notificationMuteRemaining(60_000L, nowEpochMillis = 1L),
        )
        assertEquals(
            NotificationMuteRemaining(2L, NotificationMuteRemainingUnit.HOURS),
            notificationMuteRemaining(
                mutedUntilEpochMillis = 61L * 60_000L,
                nowEpochMillis = 0L,
            ),
        )
        assertEquals(
            NotificationMuteRemaining(2L, NotificationMuteRemainingUnit.DAYS),
            notificationMuteRemaining(
                mutedUntilEpochMillis = 25L * 60L * 60_000L,
                nowEpochMillis = 0L,
            ),
        )
    }
}
