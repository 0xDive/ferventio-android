package io.ferventio.app.domain

data class PendingBackendAuthorization(
    val state: String,
    val expiresAtEpochMillis: Long,
    val serverUrl: String,
)

sealed interface BackendAuthorizationCallbackDecision {
    data class Complete(
        val serverUrl: String,
        val code: String,
        val state: String,
    ) : BackendAuthorizationCallbackDecision

    data object RejectedState : BackendAuthorizationCallbackDecision
    data object Expired : BackendAuthorizationCallbackDecision
    data object MissingServer : BackendAuthorizationCallbackDecision
    data class OAuthError(val errorCode: String) : BackendAuthorizationCallbackDecision
    data object MissingCode : BackendAuthorizationCallbackDecision
}

/**
 * Platform-neutral validation for the callback returned by the external OAuth browser.
 * Browser transport and UI error presentation stay outside this policy.
 */
object BackendAuthorizationCallbackPolicy {
    fun evaluate(
        pending: PendingBackendAuthorization,
        callbackCode: String?,
        callbackState: String?,
        callbackErrorCode: String?,
        nowEpochMillis: Long,
    ): BackendAuthorizationCallbackDecision {
        if (
            callbackState.isNullOrBlank() ||
            pending.state.isBlank() ||
            !constantTimeEquals(pending.state, callbackState)
        ) {
            return BackendAuthorizationCallbackDecision.RejectedState
        }
        if (pending.expiresAtEpochMillis <= nowEpochMillis) {
            return BackendAuthorizationCallbackDecision.Expired
        }
        if (pending.serverUrl.isBlank()) {
            return BackendAuthorizationCallbackDecision.MissingServer
        }
        if (!callbackErrorCode.isNullOrBlank()) {
            return BackendAuthorizationCallbackDecision.OAuthError(callbackErrorCode)
        }
        if (callbackCode.isNullOrBlank()) {
            return BackendAuthorizationCallbackDecision.MissingCode
        }
        return BackendAuthorizationCallbackDecision.Complete(
            serverUrl = pending.serverUrl,
            code = callbackCode,
            state = callbackState,
        )
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.isEmpty() || left.length != right.length) return false
        var difference = 0
        left.indices.forEach { index ->
            difference = difference or (left[index].code xor right[index].code)
        }
        return difference == 0
    }
}
