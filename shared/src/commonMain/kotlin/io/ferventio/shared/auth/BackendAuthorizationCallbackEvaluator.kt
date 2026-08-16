package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendAuthorizationCallbackDecision
import io.ferventio.app.domain.BackendAuthorizationCallbackPolicy
import io.ferventio.app.domain.PendingBackendAuthorization

data class BackendAuthorizationBrowserRequest(
    val authorizationUrl: String,
    val callbackScheme: String,
    val state: String,
    val expiresAtEpochMillis: Long,
    val serverUrl: String,
)

enum class BackendAuthorizationCallbackStatus {
    COMPLETE,
    REJECTED_STATE,
    EXPIRED,
    MISSING_SERVER,
    OAUTH_ERROR,
    MISSING_CODE,
}

data class BackendAuthorizationCallbackResult(
    val status: BackendAuthorizationCallbackStatus,
    val serverUrl: String? = null,
    val code: String? = null,
    val state: String? = null,
    val errorCode: String? = null,
)

/**
 * Swift-friendly facade over the shared domain callback policy.
 *
 * The native browser adapter only extracts URL query parameters. State, expiry and callback
 * acceptance rules stay in common code so Android and iOS cannot drift semantically.
 */
class BackendAuthorizationCallbackEvaluator {
    fun evaluate(
        request: BackendAuthorizationBrowserRequest,
        callbackCode: String?,
        callbackState: String?,
        callbackErrorCode: String?,
        nowEpochMillis: Long,
    ): BackendAuthorizationCallbackResult {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = PendingBackendAuthorization(
                state = request.state,
                expiresAtEpochMillis = request.expiresAtEpochMillis,
                serverUrl = request.serverUrl,
            ),
            callbackCode = callbackCode,
            callbackState = callbackState,
            callbackErrorCode = callbackErrorCode,
            nowEpochMillis = nowEpochMillis,
        )
        return when (decision) {
            is BackendAuthorizationCallbackDecision.Complete -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.COMPLETE,
                serverUrl = decision.serverUrl,
                code = decision.code,
                state = decision.state,
            )

            BackendAuthorizationCallbackDecision.RejectedState -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.REJECTED_STATE,
            )

            BackendAuthorizationCallbackDecision.Expired -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.EXPIRED,
            )

            BackendAuthorizationCallbackDecision.MissingServer -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.MISSING_SERVER,
            )

            is BackendAuthorizationCallbackDecision.OAuthError -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.OAUTH_ERROR,
                errorCode = decision.errorCode,
            )

            BackendAuthorizationCallbackDecision.MissingCode -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.MISSING_CODE,
            )
        }
    }
}
