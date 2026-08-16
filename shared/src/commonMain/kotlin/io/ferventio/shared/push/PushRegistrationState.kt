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

/**
 * Shared APNs/FCM registration state fed by platform adapters.
 *
 * Platform APIs remain native while shared runtime and UI consume a stable state model.
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
        registrationStatus = PushRegistrationStatus.REGISTERED
        deviceToken = normalizedToken
        lastRegistrationError = null
    }

    fun markRegistrationFailed(message: String?) {
        registrationStatus = PushRegistrationStatus.FAILED
        deviceToken = null
        lastRegistrationError = message?.trim()?.takeIf(String::isNotEmpty)
    }

    fun clearRegistration() {
        registrationStatus = PushRegistrationStatus.IDLE
        deviceToken = null
        lastRegistrationError = null
    }
}
