package io.ferventio.app.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPointsSessionResetTrackerTest {
    @Test
    fun `initial login does not reset empty coordinator`() {
        val tracker = ChannelPointsSessionResetTracker(initialUserId = null)

        assertFalse(tracker.shouldReset("user-a"))
    }

    @Test
    fun `logout resets existing user state`() {
        val tracker = ChannelPointsSessionResetTracker(initialUserId = "user-a")

        assertTrue(tracker.shouldReset(null))
    }

    @Test
    fun `account switch resets existing user state`() {
        val tracker = ChannelPointsSessionResetTracker(initialUserId = "user-a")

        assertTrue(tracker.shouldReset("user-b"))
    }

    @Test
    fun `same user does not reset and login after logout starts clean`() {
        val tracker = ChannelPointsSessionResetTracker(initialUserId = "user-a")

        assertFalse(tracker.shouldReset("user-a"))
        assertTrue(tracker.shouldReset(null))
        assertFalse(tracker.shouldReset("user-b"))
    }
}
