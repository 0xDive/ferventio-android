package io.ferventio.app.domain

/** Applies a direct Twitch /oauth2/validate result to a cached mobile access-token lease. */
object TwitchAccessLeaseValidation {
    fun updateAfterDirectValidation(
        cachedLease: TwitchAccessLease,
        validatedSession: TwitchSession,
        requiredScopes: Collection<String>,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): TwitchAccessLease {
        check(validatedSession.clientId == cachedLease.session.clientId) {
            "Токен выдан другому Twitch Client ID"
        }
        check(validatedSession.userId == cachedLease.session.userId) {
            "Токен выдан другому Twitch пользователю"
        }
        val missingScopes = requiredScopes.filterNot(validatedSession.scopes::contains)
        check(missingScopes.isEmpty()) {
            "Twitch не выдал необходимые разрешения: ${missingScopes.joinToString()}"
        }
        check(validatedSession.expiresInSeconds > 0L) {
            "Twitch validate вернул истёкший access token"
        }
        val remainingMillis = Math.multiplyExact(validatedSession.expiresInSeconds, 1_000L)
        val twitchExpiresAt = Math.addExact(nowEpochMillis, remainingMillis)
        return cachedLease.copy(
            twitchExpiresAtEpochMillis = twitchExpiresAt,
            twitchValidatedAtEpochMillis = nowEpochMillis,
            session = validatedSession,
        )
    }
}
