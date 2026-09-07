package io.ferventio.shared.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AccountMutationState(
    val isReauthorizing: Boolean = false,
    val isRevokingDevice: Boolean = false,
    val isRevokingAllSessions: Boolean = false,
    val errorMessage: String? = null,
) {
    val mutationInFlight: Boolean
        get() = isReauthorizing || isRevokingDevice || isRevokingAllSessions
}

class AccountMutationStateHolder {
    var state by mutableStateOf(AccountMutationState())
        private set

    fun beginReauthorization(): Boolean {
        if (state.mutationInFlight) return false
        state = AccountMutationState(isReauthorizing = true)
        return true
    }

    fun beginRevokeDevice(): Boolean {
        if (state.mutationInFlight) return false
        state = AccountMutationState(isRevokingDevice = true)
        return true
    }

    fun beginRevokeAllSessions(): Boolean {
        if (state.mutationInFlight) return false
        state = AccountMutationState(isRevokingAllSessions = true)
        return true
    }

    fun finishMutation() {
        state = AccountMutationState()
    }

    fun failMutation(errorMessage: String?) {
        state = AccountMutationState(
            errorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    fun clearError() {
        if (state.errorMessage == null) return
        state = state.copy(errorMessage = null)
    }

    fun clear() {
        state = AccountMutationState()
    }
}
