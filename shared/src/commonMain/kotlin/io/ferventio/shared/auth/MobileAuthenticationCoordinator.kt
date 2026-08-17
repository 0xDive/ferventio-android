package io.ferventio.shared.auth

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchAccessLeasePolicy
import kotlin.Throws
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException

enum class MobileAuthenticationFailureReason {
    NOT_CALLBACK,
    INVALID_CALLBACK,
    REJECTED_STATE,
    EXPIRED,
    MISSING_SERVER,
    OAUTH_ERROR,
    MISSING_CODE,
    MALFORMED_COMPLETION,
}

class MobileAuthenticationFlowException(
    val reason: MobileAuthenticationFailureReason,
    val errorCode: String? = null,
) : IllegalStateException(
    buildString {
        append("Mobile authentication failed: ")
        append(reason.name)
        if (!errorCode.isNullOrBlank()) {
            append(" (")
            append(errorCode)
            append(')')
        }
    },
)

data class MobileAuthenticationRefreshResult(
    val authentication: StoredAuthentication?,
    val shouldPersist: Boolean,
    val shouldSignOut: Boolean,
) {
    init {
        require(!shouldPersist || authentication != null) {
            "Persistable authentication refresh must include authentication"
        }
        require(!shouldSignOut || authentication == null) {
            "Signed-out authentication refresh must not include authentication"
        }
    }
}

/**
 * Shared orchestration for the mobile backend authorization handshake and persisted-session restore.
 * Native code remains responsible only for secure identity storage and external-browser transport.
 */
class MobileAuthenticationCoordinator(
    private val backend: MobileBackendAuthenticationClient = MobileBackendAuthenticationClient(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Explicit constructor for Kotlin/Native export; default primary arguments are not a Swift init(). */
    constructor() : this(
        backend = MobileBackendAuthenticationClient(),
        nowEpochMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    @Throws(Exception::class)
    suspend fun startAuthorization(
        serverUrl: String,
        identity: MobileDeviceIdentity,
        callbackScheme: String,
    ): BackendAuthorizationBrowserRequest {
        MobileDeviceIdentityValidation.requireValid(identity)
        val scheme = callbackScheme.trim()
        require(CALLBACK_SCHEME.matches(scheme)) { "Invalid OAuth callback scheme" }
        val start = backend.startAuthorization(
            serverUrl = serverUrl,
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
            appCallbackUri = "$scheme://oauth/callback",
        )
        return BackendAuthorizationBrowserRequest(
            authorizationUrl = start.authorizationUrl,
            callbackScheme = scheme,
            state = start.state,
            expiresAtEpochMillis = start.expiresAtEpochMillis,
            serverUrl = serverUrl.trim().trimEnd('/'),
        )
    }

    @Throws(Exception::class)
    suspend fun restoreAuthentication(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): StoredAuthentication {
        MobileDeviceIdentityValidation.requireValid(identity)
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val now = nowEpochMillis()
        if (authentication.backendCredential.expiresAtEpochMillis <= now) {
            throw MobileAuthenticationFlowException(MobileAuthenticationFailureReason.EXPIRED)
        }
        val cachedLease = authentication.accessLease
        if (
            cachedLease != null &&
            TwitchAccessLeasePolicy.canReuseWithoutBackendCall(cachedLease, now)
        ) {
            return authentication
        }
        val refreshedLease = backend.leaseAccessToken(
            storedAuthentication = authentication,
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
            forceRefresh = false,
        )
        return StoredAuthentication(
            backendCredential = authentication.backendCredential,
            accessLease = refreshedLease,
        )
    }

    /**
     * Refreshes an already signed-in mobile session when the app returns to foreground.
     *
     * A short backend lease is renewed before platform code restarts EventSub. During a transient
     * backend outage, the existing Twitch credential may still be reused under the narrower
     * stale-if-error domain policy. A missing/expired device session is terminal; temporary failures
     * without a safe cached credential return an unavailable result without destroying local state.
     */
    @Throws(Exception::class)
    suspend fun refreshAuthenticationForForeground(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): MobileAuthenticationRefreshResult {
        try {
            MobileDeviceIdentityValidation.requireValid(identity)
            AuthenticationPersistenceValidation.requireValid(
                authentication.backendCredential,
                authentication.accessLease,
            )
        } catch (_: IllegalArgumentException) {
            return signedOutRefresh()
        }

        val now = nowEpochMillis()
        if (authentication.backendCredential.expiresAtEpochMillis <= now) {
            return signedOutRefresh()
        }

        val cachedLease = authentication.accessLease
        if (
            cachedLease != null &&
            TwitchAccessLeasePolicy.canReuseWithoutBackendCall(cachedLease, now)
        ) {
            return readyRefresh(authentication, shouldPersist = false)
        }

        val refreshedLease = try {
            backend.leaseAccessToken(
                storedAuthentication = authentication,
                installationId = identity.installationId,
                deviceSecret = identity.deviceSecret,
                forceRefresh = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: MobileBackendAuthenticationException) {
            return when {
                error.statusCode == 401 || error.statusCode == 403 -> signedOutRefresh()
                error.statusCode.isTransientBackendStatus() ->
                    outageFallback(authentication, cachedLease, now)
                else -> unavailableRefresh()
            }
        } catch (_: Exception) {
            return outageFallback(authentication, cachedLease, now)
        }

        return readyRefresh(
            authentication = StoredAuthentication(
                backendCredential = authentication.backendCredential,
                accessLease = refreshedLease,
            ),
            shouldPersist = true,
        )
    }

    /**
     * Forces a fresh backend lease after Twitch has explicitly rejected the cached credential.
     * A rejected Twitch token must never use the stale-if-error fallback, even when its local
     * expiry metadata still says that it is reusable.
     */
    @Throws(Exception::class)
    suspend fun refreshAuthenticationAfterRejection(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): MobileAuthenticationRefreshResult {
        try {
            MobileDeviceIdentityValidation.requireValid(identity)
            AuthenticationPersistenceValidation.requireValid(
                authentication.backendCredential,
                authentication.accessLease,
            )
        } catch (_: IllegalArgumentException) {
            return signedOutRefresh()
        }

        if (authentication.backendCredential.expiresAtEpochMillis <= nowEpochMillis()) {
            return signedOutRefresh()
        }

        val refreshedLease = try {
            backend.leaseAccessToken(
                storedAuthentication = authentication,
                installationId = identity.installationId,
                deviceSecret = identity.deviceSecret,
                forceRefresh = true,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: MobileBackendAuthenticationException) {
            return if (error.statusCode == 401 || error.statusCode == 403) {
                signedOutRefresh()
            } else {
                unavailableRefresh()
            }
        } catch (_: Exception) {
            return unavailableRefresh()
        }

        return readyRefresh(
            authentication = StoredAuthentication(
                backendCredential = authentication.backendCredential,
                accessLease = refreshedLease,
            ),
            shouldPersist = true,
        )
    }

    @Throws(Exception::class)
    suspend fun completeAuthorization(
        identity: MobileDeviceIdentity,
        callback: BackendAuthorizationCallbackResult,
    ): StoredAuthentication {
        MobileDeviceIdentityValidation.requireValid(identity)
        if (callback.status != BackendAuthorizationCallbackStatus.COMPLETE) {
            throw MobileAuthenticationFlowException(
                reason = callback.status.toFailureReason(),
                errorCode = callback.errorCode,
            )
        }
        val serverUrl = callback.serverUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: throw MobileAuthenticationFlowException(
                MobileAuthenticationFailureReason.MALFORMED_COMPLETION,
            )
        val code = callback.code?.trim()?.takeIf(String::isNotEmpty)
            ?: throw MobileAuthenticationFlowException(
                MobileAuthenticationFailureReason.MALFORMED_COMPLETION,
            )
        val state = callback.state?.trim()?.takeIf(String::isNotEmpty)
            ?: throw MobileAuthenticationFlowException(
                MobileAuthenticationFailureReason.MALFORMED_COMPLETION,
            )
        return backend.completeAuthorization(
            serverUrl = serverUrl,
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
            code = code,
            state = state,
        )
    }

    @Throws(Exception::class)
    suspend fun revokeDevice(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ) {
        MobileDeviceIdentityValidation.requireValid(identity)
        AuthenticationPersistenceValidation.requireValidBackendCredential(
            authentication.backendCredential,
        )
        backend.revokeDevice(
            storedAuthentication = authentication,
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
        )
    }

    private fun outageFallback(
        authentication: StoredAuthentication,
        cachedLease: TwitchAccessLease?,
        nowEpochMillis: Long,
    ): MobileAuthenticationRefreshResult =
        if (
            cachedLease != null &&
            TwitchAccessLeasePolicy.canUseDuringBackendOutage(cachedLease, nowEpochMillis)
        ) {
            readyRefresh(authentication, shouldPersist = false)
        } else {
            unavailableRefresh()
        }

    private fun readyRefresh(
        authentication: StoredAuthentication,
        shouldPersist: Boolean,
    ) = MobileAuthenticationRefreshResult(
        authentication = authentication,
        shouldPersist = shouldPersist,
        shouldSignOut = false,
    )

    private fun signedOutRefresh() = MobileAuthenticationRefreshResult(
        authentication = null,
        shouldPersist = false,
        shouldSignOut = true,
    )

    private fun unavailableRefresh() = MobileAuthenticationRefreshResult(
        authentication = null,
        shouldPersist = false,
        shouldSignOut = false,
    )

    private fun Int.isTransientBackendStatus(): Boolean =
        this == 408 || this == 425 || this == 429 || this in 500..599

    private fun BackendAuthorizationCallbackStatus.toFailureReason(): MobileAuthenticationFailureReason =
        when (this) {
            BackendAuthorizationCallbackStatus.NOT_CALLBACK -> MobileAuthenticationFailureReason.NOT_CALLBACK
            BackendAuthorizationCallbackStatus.INVALID_CALLBACK -> MobileAuthenticationFailureReason.INVALID_CALLBACK
            BackendAuthorizationCallbackStatus.REJECTED_STATE -> MobileAuthenticationFailureReason.REJECTED_STATE
            BackendAuthorizationCallbackStatus.EXPIRED -> MobileAuthenticationFailureReason.EXPIRED
            BackendAuthorizationCallbackStatus.MISSING_SERVER -> MobileAuthenticationFailureReason.MISSING_SERVER
            BackendAuthorizationCallbackStatus.OAUTH_ERROR -> MobileAuthenticationFailureReason.OAUTH_ERROR
            BackendAuthorizationCallbackStatus.MISSING_CODE -> MobileAuthenticationFailureReason.MISSING_CODE
            BackendAuthorizationCallbackStatus.COMPLETE -> MobileAuthenticationFailureReason.MALFORMED_COMPLETION
        }

    private companion object {
        val CALLBACK_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")
    }
}
