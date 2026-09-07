package io.ferventio.shared.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.StoredAuthentication

enum class MobileAuthenticationStatus {
    RESTORING,
    SIGNED_OUT,
    AUTHORIZING,
    SIGNED_IN,
    FAILED,
}

data class MobileAuthenticationState(
    val status: MobileAuthenticationStatus = MobileAuthenticationStatus.RESTORING,
    val authentication: StoredAuthentication? = null,
    val errorMessage: String? = null,
)

class MobileAuthenticationStateHolder {
    var state by mutableStateOf(MobileAuthenticationState())
        private set

    fun restore(authentication: StoredAuthentication?) {
        state = if (authentication == null) {
            MobileAuthenticationState(status = MobileAuthenticationStatus.SIGNED_OUT)
        } else {
            MobileAuthenticationState(
                status = MobileAuthenticationStatus.SIGNED_IN,
                authentication = authentication,
            )
        }
    }

    fun beginAuthorization() {
        state = MobileAuthenticationState(status = MobileAuthenticationStatus.AUTHORIZING)
    }

    fun markSignedIn(authentication: StoredAuthentication) {
        state = MobileAuthenticationState(
            status = MobileAuthenticationStatus.SIGNED_IN,
            authentication = authentication,
        )
    }

    fun markFailed(errorMessage: String?) {
        state = MobileAuthenticationState(
            status = MobileAuthenticationStatus.FAILED,
            errorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    fun signOut() {
        state = MobileAuthenticationState(status = MobileAuthenticationStatus.SIGNED_OUT)
    }
}
