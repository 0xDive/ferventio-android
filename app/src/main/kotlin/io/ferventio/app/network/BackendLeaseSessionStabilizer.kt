package io.ferventio.app.network

import io.ferventio.app.domain.TwitchSession
import java.security.MessageDigest

/**
 * A backend lease is renewed much more often than the underlying Twitch access token.
 * `expiresInSeconds` therefore changes on every lease response even though EventSub can
 * keep using the existing WebSocket. Reuse the previous session snapshot while the
 * actual transport identity is unchanged so lease bookkeeping cannot look like an
 * account/token transition to connection code.
 *
 * Absolute token deadlines remain authoritative on TwitchAccessLease; the retained
 * expiresInSeconds value is only the validation snapshot associated with this token.
 */
internal object BackendLeaseSessionStabilizer {
    private val lock = Any()
    private var accessTokenFingerprint: String? = null
    private var session: TwitchSession? = null

    fun stabilize(
        accessToken: String,
        candidate: TwitchSession,
    ): TwitchSession = synchronized(lock) {
        val fingerprint = accessToken.sha256Fingerprint()
        val previous = session
        if (
            accessTokenFingerprint == fingerprint &&
            previous != null &&
            previous.sameTransportIdentity(candidate)
        ) {
            previous
        } else {
            accessTokenFingerprint = fingerprint
            session = candidate
            candidate
        }
    }

    internal fun clearForTest() = synchronized(lock) {
        accessTokenFingerprint = null
        session = null
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

private fun String.sha256Fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private fun TwitchSession.sameTransportIdentity(other: TwitchSession): Boolean =
    clientId == other.clientId &&
        userId == other.userId &&
        login == other.login &&
        scopes == other.scopes
