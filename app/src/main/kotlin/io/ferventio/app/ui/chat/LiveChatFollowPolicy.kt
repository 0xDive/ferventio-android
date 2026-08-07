package io.ferventio.app.ui

import kotlin.math.max

/** Pure policy for intentional live-follow pause/resume gestures. */
internal object LiveChatFollowPolicy {
    fun updateResumeEligibility(
        current: Boolean,
        viewportAtBottom: Boolean,
    ): Boolean = current || !viewportAtBottom

    fun accumulateUpwardDrag(
        currentPx: Float,
        availableY: Float,
    ): Float = max(0f, currentPx - availableY)

    fun shouldPauseForUserDrag(
        accumulatedUpwardDragPx: Float,
        pauseThresholdPx: Float,
    ): Boolean = pauseThresholdPx > 0f && accumulatedUpwardDragPx >= pauseThresholdPx

    fun shouldResumeAfterUserScroll(
        autoScrollEnabled: Boolean,
        resumeEligibleAfterUserScroll: Boolean,
        viewportAtBottom: Boolean,
    ): Boolean = autoScrollEnabled && resumeEligibleAfterUserScroll && viewportAtBottom
}
