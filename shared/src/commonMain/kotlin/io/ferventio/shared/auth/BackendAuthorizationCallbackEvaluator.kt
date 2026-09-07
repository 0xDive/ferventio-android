package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendAuthorizationCallbackDecision
import io.ferventio.app.domain.BackendAuthorizationCallbackPolicy
import io.ferventio.app.domain.MobileAuthorizationCallbackComponents
import io.ferventio.app.domain.MobileAuthorizationCallbackParseResult
import io.ferventio.app.domain.MobileAuthorizationCallbackParser
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
    NOT_CALLBACK,
    INVALID_CALLBACK,
}

data class BackendAuthorizationCallbackResult(
    val status: BackendAuthorizationCallbackStatus,
    val serverUrl: String? = null,
    val code: String? = null,
    val state: String? = null,
    val errorCode: String? = null,
)

/**
 * Swift-friendly facade over shared callback route parsing and domain acceptance policy.
 *
 * Native browser adapters only expose already-decoded URL components. Route shape, duplicate
 * query rejection, state, expiry and callback acceptance rules stay in common code so Android
 * and iOS cannot drift semantically.
 */
class BackendAuthorizationCallbackEvaluator {
    fun evaluateComponents(
        request: BackendAuthorizationBrowserRequest,
        callbackScheme: String?,
        callbackHost: String?,
        callbackPath: String?,
        callbackHasUserInfo: Boolean,
        callbackFragment: String?,
        callbackCodeValues: List<String?>,
        callbackStateValues: List<String?>,
        callbackErrorCodeValues: List<String?>,
        nowEpochMillis: Long,
    ): BackendAuthorizationCallbackResult {
        return when (
            val parsed = MobileAuthorizationCallbackParser.parse(
                components = MobileAuthorizationCallbackComponents(
                    scheme = callbackScheme,
                    host = callbackHost,
                    path = callbackPath,
                    hasUserInfo = callbackHasUserInfo,
                    fragment = callbackFragment,
                    codeValues = callbackCodeValues,
                    stateValues = callbackStateValues,
                    errorValues = callbackErrorCodeValues,
                ),
                expectedScheme = request.callbackScheme,
            )
        ) {
            MobileAuthorizationCallbackParseResult.NotCallback -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.NOT_CALLBACK,
            )

            MobileAuthorizationCallbackParseResult.InvalidCallback -> BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.INVALID_CALLBACK,
            )

            is MobileAuthorizationCallbackParseResult.Parsed -> evaluate(
                request = request,
                callbackCode = parsed.payload.code,
                callbackState = parsed.payload.state,
                callbackErrorCode = parsed.payload.errorCode,
                nowEpochMillis = nowEpochMillis,
            )
        }
    }

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
