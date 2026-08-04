package io.ferventio.app.domain

/** Rules for normal lease reuse and the narrower stale-if-error backend-outage fallback. */
object TwitchAccessLeasePolicy {
    private const val NORMAL_SAFETY_WINDOW_MILLIS = 5_000L
    private const val OUTAGE_SAFETY_WINDOW_MILLIS = 30_000L
    private const val STARTUP_VALIDATION_TRUST_WINDOW_MILLIS = 5_000L
    private const val DIRECT_VALIDATION_INTERVAL_MILLIS = 55L * 60L * 1_000L

    fun canReuseWithoutBackendCall(
        lease: TwitchAccessLease,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = isStructurallyUsable(lease) &&
        lease.leaseExpiresAtEpochMillis > nowEpochMillis + NORMAL_SAFETY_WINDOW_MILLIS

    fun canUseDuringBackendOutage(
        lease: TwitchAccessLease,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = isStructurallyUsable(lease) &&
        lease.twitchExpiresAtEpochMillis > nowEpochMillis + OUTAGE_SAFETY_WINDOW_MILLIS

    fun needsDirectValidationAtStartup(
        lease: TwitchAccessLease,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = lease.twitchValidatedAtEpochMillis <= nowEpochMillis - STARTUP_VALIDATION_TRUST_WINDOW_MILLIS

    fun needsDirectValidationDuringOutage(
        lease: TwitchAccessLease,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = lease.twitchValidatedAtEpochMillis <= nowEpochMillis - DIRECT_VALIDATION_INTERVAL_MILLIS

    /** Ignores the rolling short-lease deadline and derived expiresIn value to avoid disk writes every renewal. */
    fun representsSameCachedCredential(
        left: TwitchAccessLease,
        right: TwitchAccessLease,
    ): Boolean = left.accessToken == right.accessToken &&
        left.twitchExpiresAtEpochMillis == right.twitchExpiresAtEpochMillis &&
        left.twitchValidatedAtEpochMillis == right.twitchValidatedAtEpochMillis &&
        left.backendSessionExpiresAtEpochMillis == right.backendSessionExpiresAtEpochMillis &&
        left.session.clientId == right.session.clientId &&
        left.session.userId == right.session.userId &&
        left.session.login == right.session.login &&
        left.session.scopes == right.session.scopes

    private fun isStructurallyUsable(lease: TwitchAccessLease): Boolean =
        lease.accessToken.isNotBlank() &&
            lease.session.clientId.isNotBlank() &&
            lease.session.userId.isNotBlank() &&
            lease.session.login.isNotBlank() &&
            lease.session.scopes.none(String::isBlank) &&
            lease.twitchValidatedAtEpochMillis > 0L
}
