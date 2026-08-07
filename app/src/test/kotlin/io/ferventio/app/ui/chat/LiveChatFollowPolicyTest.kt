package io.ferventio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatFollowPolicyTest {
    @Test
    fun `incidental upward movement stays below pause threshold`() {
        val drag = LiveChatFollowPolicy.accumulateUpwardDrag(
            currentPx = 0f,
            availableY = -3f,
        )

        assertEquals(3f, drag, 0f)
        assertFalse(
            LiveChatFollowPolicy.shouldPauseForUserDrag(
                accumulatedUpwardDragPx = drag,
                pauseThresholdPx = 24f,
            ),
        )
    }

    @Test
    fun `intentional upward drag pauses after threshold`() {
        var drag = 0f
        drag = LiveChatFollowPolicy.accumulateUpwardDrag(drag, availableY = -10f)
        drag = LiveChatFollowPolicy.accumulateUpwardDrag(drag, availableY = -15f)

        assertTrue(
            LiveChatFollowPolicy.shouldPauseForUserDrag(
                accumulatedUpwardDragPx = drag,
                pauseThresholdPx = 24f,
            ),
        )
    }

    @Test
    fun `downward correction reduces accumulated upward intent`() {
        var drag = LiveChatFollowPolicy.accumulateUpwardDrag(0f, availableY = -20f)
        drag = LiveChatFollowPolicy.accumulateUpwardDrag(drag, availableY = 8f)

        assertEquals(12f, drag, 0f)
        assertFalse(LiveChatFollowPolicy.shouldPauseForUserDrag(drag, pauseThresholdPx = 24f))
    }

    @Test
    fun `return from older content to the bottom resumes following`() {
        assertTrue(
            LiveChatFollowPolicy.shouldResumeAfterUserScroll(
                autoScrollEnabled = true,
                resumeEligibleAfterUserScroll = true,
                viewportAtBottom = true,
            ),
        )
    }

    @Test
    fun `remaining above the bottom stays paused`() {
        assertFalse(
            LiveChatFollowPolicy.shouldResumeAfterUserScroll(
                autoScrollEnabled = true,
                resumeEligibleAfterUserScroll = true,
                viewportAtBottom = false,
            ),
        )
    }

    @Test
    fun `disabled auto scroll never resumes following`() {
        assertFalse(
            LiveChatFollowPolicy.shouldResumeAfterUserScroll(
                autoScrollEnabled = false,
                resumeEligibleAfterUserScroll = true,
                viewportAtBottom = true,
            ),
        )
    }

    @Test
    fun `short movement that remains at bottom is not eligible for automatic resume`() {
        assertFalse(
            LiveChatFollowPolicy.updateResumeEligibility(
                current = false,
                viewportAtBottom = true,
            ),
        )
    }

    @Test
    fun `moving into older content makes resume eligibility sticky`() {
        assertTrue(
            LiveChatFollowPolicy.updateResumeEligibility(
                current = false,
                viewportAtBottom = false,
            ),
        )
        assertTrue(
            LiveChatFollowPolicy.updateResumeEligibility(
                current = true,
                viewportAtBottom = true,
            ),
        )
    }
}
