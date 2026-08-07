package io.ferventio.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatFollowPolicyTest {
    @Test
    fun `short drag at the bottom keeps following paused`() {
        assertFalse(
            LiveChatFollowPolicy.shouldResumeAfterUserScroll(
                autoScrollEnabled = true,
                resumeEligibleAfterUserScroll = false,
                viewportAtBottom = true,
            ),
        )
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
