package io.ferventio.app.domain

import kotlin.time.Clock

/** Applies a direct Twitch /oauth2/validate result to a cached mobile access-token lease. */
object TwitchAccessLeaseValidation {
    fun updateAfterDirectValidation(
        cachedLease: TwitchAccessLease,
        validatedSession: TwitchSession,
        requiredScopes: Collection<String>,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
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
        val remainingMillis = multiplyExact(validatedSession.expiresInSeconds, 1_000L)
        val twitchExpiresAt = addExact(nowEpochMillis, remainingMillis)
        val stableSession = if (cachedLease.session.sameTransportIdentity(validatedSession)) {
            cachedLease.session
        } else {
            validatedSession
        }
        return cachedLease.copy(
            twitchExpiresAtEpochMillis = twitchExpiresAt,
            twitchValidatedAtEpochMillis = nowEpochMillis,
            session = stableSession,
        )
    }

    private fun TwitchSession.sameTransportIdentity(other: TwitchSession): Boolean =
        clientId == other.clientId &&
            userId == other.userId &&
            login == other.login &&
            scopes == other.scopes

    private fun multiplyExact(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        if (left == Long.MIN_VALUE && right == -1L || right == Long.MIN_VALUE && left == -1L) {
            throw ArithmeticException("long overflow")
        }
        val result = left * right
        if (result / right != left) throw ArithmeticException("long overflow")
        return result
    }

    private fun addExact(left: Long, right: Long): Long {
        val result = left + right
        if ((left xor result) and (right xor result) < 0L) {
            throw ArithmeticException("long overflow")
        }
        return result
    }
}
