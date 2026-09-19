package io.ferventio.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class BackendAuthorizationCallbackEvaluatorTest {
    private val evaluator = BackendAuthorizationCallbackEvaluator()

    @Test
    fun returnsCompletionForMatchingFreshCallback() {
        val request = request()

        val result = evaluator.evaluate(
            request = request,
            callbackCode = "authorization-code",
            callbackState = request.state,
            callbackErrorCode = null,
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.COMPLETE, result.status)
        assertEquals(request.serverUrl, result.serverUrl)
        assertEquals("authorization-code", result.code)
        assertEquals(request.state, result.state)
    }

    @Test
    fun evaluatesStrictCallbackComponentsBeforeStatePolicy() {
        val request = request()

        val result = evaluator.evaluateComponents(
            request = request,
            callbackScheme = request.callbackScheme,
            callbackHost = "oauth",
            callbackPath = "/callback",
            callbackHasUserInfo = false,
            callbackFragment = null,
            callbackCodeValues = listOf(" authorization-code "),
            callbackStateValues = listOf(" ${request.state} "),
            callbackErrorCodeValues = emptyList(),
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.COMPLETE, result.status)
        assertEquals("authorization-code", result.code)
        assertEquals(request.state, result.state)
    }

    @Test
    fun rejectsDuplicateStateBeforePolicyEvaluation() {
        val request = request()

        val result = evaluator.evaluateComponents(
            request = request,
            callbackScheme = request.callbackScheme,
            callbackHost = "oauth",
            callbackPath = "/callback",
            callbackHasUserInfo = false,
            callbackFragment = null,
            callbackCodeValues = listOf("authorization-code"),
            callbackStateValues = listOf(request.state, "unexpected-state"),
            callbackErrorCodeValues = emptyList(),
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.INVALID_CALLBACK, result.status)
    }

    @Test
    fun rejectsUnsafeCallbackRouteComponents() {
        val request = request()

        val result = evaluator.evaluateComponents(
            request = request,
            callbackScheme = request.callbackScheme,
            callbackHost = "oauth",
            callbackPath = "/callback",
            callbackHasUserInfo = true,
            callbackFragment = null,
            callbackCodeValues = listOf("authorization-code"),
            callbackStateValues = listOf(request.state),
            callbackErrorCodeValues = emptyList(),
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.INVALID_CALLBACK, result.status)
    }

    @Test
    fun rejectsMismatchedStateBeforeReturningOAuthError() {
        val result = evaluator.evaluate(
            request = request(),
            callbackCode = null,
            callbackState = "unexpected-state",
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.REJECTED_STATE, result.status)
    }

    @Test
    fun preservesOAuthErrorCode() {
        val request = request()

        val result = evaluator.evaluate(
            request = request,
            callbackCode = null,
            callbackState = request.state,
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertEquals(BackendAuthorizationCallbackStatus.OAUTH_ERROR, result.status)
        assertEquals("access_denied", result.errorCode)
    }

    private fun request() = BackendAuthorizationBrowserRequest(
        authorizationUrl = "https://example.test/oauth/authorize",
        callbackScheme = "ferventio",
        state = "expected-state",
        expiresAtEpochMillis = 2_000L,
        serverUrl = "https://example.test",
    )
}
