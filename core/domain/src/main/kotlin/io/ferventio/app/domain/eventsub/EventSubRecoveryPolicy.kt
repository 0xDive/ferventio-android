package io.ferventio.app.domain

object EventSubRecoveryPolicy {
    fun shouldReconnect(
        isAuthenticated: Boolean,
        channelCount: Int,
        hasActiveJob: Boolean,
        hasSession: Boolean,
        hasAccessToken: Boolean,
        networkAvailable: Boolean,
    ): Boolean =
        isAuthenticated &&
            channelCount > 0 &&
            !hasActiveJob &&
            hasSession &&
            hasAccessToken &&
            networkAvailable
}
