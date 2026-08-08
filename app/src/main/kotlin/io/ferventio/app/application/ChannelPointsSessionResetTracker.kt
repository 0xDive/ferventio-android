package io.ferventio.app.application

/**
 * Tracks Twitch user-session transitions for viewer-specific Channel Points state.
 * Initial login starts from an empty coordinator and therefore does not need a reset;
 * leaving or replacing an existing authenticated user does.
 */
internal class ChannelPointsSessionResetTracker(
    initialUserId: String?,
) {
    private var previousUserId: String? = initialUserId

    fun shouldReset(nextUserId: String?): Boolean {
        val reset = previousUserId != null && nextUserId != previousUserId
        previousUserId = nextUserId
        return reset
    }
}
