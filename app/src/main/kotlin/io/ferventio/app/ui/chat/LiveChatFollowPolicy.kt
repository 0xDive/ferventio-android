package io.ferventio.app.ui

/** Pure policy for deciding when a completed user scroll may resume live following. */
internal object LiveChatFollowPolicy {
    fun updateResumeEligibility(
        current: Boolean,
        viewportAtBottom: Boolean,
    ): Boolean = current || !viewportAtBottom

    fun shouldResumeAfterUserScroll(
        autoScrollEnabled: Boolean,
        resumeEligibleAfterUserScroll: Boolean,
        viewportAtBottom: Boolean,
    ): Boolean = autoScrollEnabled && resumeEligibleAfterUserScroll && viewportAtBottom
}
