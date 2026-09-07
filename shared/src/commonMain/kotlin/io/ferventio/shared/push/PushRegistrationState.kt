package io.ferventio.shared.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PushAuthorizationStatus {
    UNKNOWN,
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
    PROVISIONAL,
    EPHEMERAL,
}

enum class PushRegistrationStatus {
    IDLE,
    REQUESTED,
    REGISTERED,
    FAILED,
}

enum class PushBackendRegistrationStatus {
    IDLE,
    REGISTERING,
    REGISTERED,
    FAILED,
}

/**
 * Shared push state fed by platform adapters and backend-registration orchestration.
 *
 * Platform token acquisition and Ferventio backend registration are intentionally tracked as
 * separate lifecycles: an APNs/FCM token can be valid even while backend synchronization fails.
 */
class PushRegistrationStateHolder(
    initialAuthorizationStatus: PushAuthorizationStatus,
) {
    constructor() : this(PushAuthorizationStatus.UNKNOWN)

    var authorizationStatus by mutableStateOf(initialAuthorizationStatus)
        private set

    var registrationStatus by mutableStateOf(PushRegistrationStatus.IDLE)
        private set

    var deviceToken by mutableStateOf<String?>(null)
        private set

    var lastRegistrationError by mutableStateOf<String?>(null)
        private set

    var backendRegistrationStatus by mutableStateOf(PushBackendRegistrationStatus.IDLE)
        private set

    var backendRegisteredDeviceToken by mutableStateOf<String?>(null)
        private set

    var lastBackendRegistrationError by mutableStateOf<String?>(null)
        private set

    val needsBackendRegistration: Boolean
        get() = deviceToken != null &&
            (
                backendRegistrationStatus != PushBackendRegistrationStatus.REGISTERED ||
                    backendRegisteredDeviceToken != deviceToken
                )

    fun updateAuthorizationStatus(status: PushAuthorizationStatus) {
        authorizationStatus = status
    }

    fun markAuthorizationUnknown() {
        updateAuthorizationStatus(PushAuthorizationStatus.UNKNOWN)
    }

    fun markAuthorizationNotDetermined() {
        updateAuthorizationStatus(PushAuthorizationStatus.NOT_DETERMINED)
    }

    fun markAuthorizationDenied() {
        updateAuthorizationStatus(PushAuthorizationStatus.DENIED)
    }

    fun markAuthorizationAuthorized() {
        updateAuthorizationStatus(PushAuthorizationStatus.AUTHORIZED)
    }

    fun markAuthorizationProvisional() {
        updateAuthorizationStatus(PushAuthorizationStatus.PROVISIONAL)
    }

    fun markAuthorizationEphemeral() {
        updateAuthorizationStatus(PushAuthorizationStatus.EPHEMERAL)
    }

    fun markRegistrationRequested() {
        registrationStatus = PushRegistrationStatus.REQUESTED
        lastRegistrationError = null
    }

    fun markRegistered(token: String) {
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "Push device token must not be blank" }
        val tokenChanged = deviceToken != normalizedToken
        registrationStatus = PushRegistrationStatus.REGISTERED
        deviceToken = normalizedToken
        lastRegistrationError = null
        if (tokenChanged) {
            clearBackendRegistration()
        }
    }

    fun markRegistrationFailed(message: String?) {
        registrationStatus = PushRegistrationStatus.FAILED
        deviceToken = null
        lastRegistrationError = message?.trim()?.takeIf(String::isNotEmpty)
        clearBackendRegistration()
    }

    fun clearRegistration() {
        registrationStatus = PushRegistrationStatus.IDLE
        deviceToken = null
        lastRegistrationError = null
        clearBackendRegistration()
    }

    fun markBackendRegistrationStarted() {
        require(!deviceToken.isNullOrBlank()) {
            "Push device token is required before backend registration"
        }
        backendRegistrationStatus = PushBackendRegistrationStatus.REGISTERING
        backendRegisteredDeviceToken = null
        lastBackendRegistrationError = null
    }

    fun markBackendRegistered() {
        val token = deviceToken?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Push device token is required before backend registration")
        backendRegistrationStatus = PushBackendRegistrationStatus.REGISTERED
        backendRegisteredDeviceToken = token
        lastBackendRegistrationError = null
    }

    fun markBackendRegistrationFailed(message: String?) {
        backendRegistrationStatus = PushBackendRegistrationStatus.FAILED
        backendRegisteredDeviceToken = null
        lastBackendRegistrationError = message?.trim()?.takeIf(String::isNotEmpty)
    }

    fun clearBackendRegistration() {
        backendRegistrationStatus = PushBackendRegistrationStatus.IDLE
        backendRegisteredDeviceToken = null
        lastBackendRegistrationError = null
    }
}
