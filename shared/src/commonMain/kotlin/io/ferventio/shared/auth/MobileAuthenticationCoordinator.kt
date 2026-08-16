package io.ferventio.shared.auth

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLeasePolicy
import kotlin.Throws
import kotlin.time.Clock

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

/**
 * Shared orchestration for the mobile backend authorization handshake and persisted-session restore.
 * Native code remains responsible only for secure identity storage and external-browser transport.
 */
class MobileAuthenticationCoordinator(
    private val backend: MobileBackendAuthenticationClient = MobileBackendAuthenticationClient(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
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
