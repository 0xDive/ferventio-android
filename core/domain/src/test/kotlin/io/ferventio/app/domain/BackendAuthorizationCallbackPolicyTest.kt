package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackendAuthorizationCallbackPolicyTest {
    @Test
    fun `matching callback completes with pending server`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(),
            callbackCode = "code",
            callbackState = "state",
            callbackErrorCode = null,
            nowEpochMillis = 1_000L,
        )

        assertEquals(
            BackendAuthorizationCallbackDecision.Complete(
                serverUrl = "https://auth.example.test",
                code = "code",
                state = "state",
            ),
            decision,
        )
    }

    @Test
    fun `foreign state is rejected before callback error`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(),
            callbackCode = null,
            callbackState = "foreign",
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertIs<BackendAuthorizationCallbackDecision.RejectedState>(decision)
    }

    @Test
    fun `expired callback is rejected before OAuth error`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(expiresAtEpochMillis = 1_000L),
            callbackCode = null,
            callbackState = "state",
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertIs<BackendAuthorizationCallbackDecision.Expired>(decision)
    }

    @Test
    fun `missing pending server is rejected before OAuth error`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(serverUrl = " "),
            callbackCode = null,
            callbackState = "state",
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertIs<BackendAuthorizationCallbackDecision.MissingServer>(decision)
    }

    @Test
    fun `OAuth error is preserved for presentation layer`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(),
            callbackCode = null,
            callbackState = "state",
            callbackErrorCode = "access_denied",
            nowEpochMillis = 1_000L,
        )

        assertEquals(
            BackendAuthorizationCallbackDecision.OAuthError("access_denied"),
            decision,
        )
    }

    @Test
    fun `missing code is rejected after state and expiry validation`() {
        val decision = BackendAuthorizationCallbackPolicy.evaluate(
            pending = pending(),
            callbackCode = " ",
            callbackState = "state",
            callbackErrorCode = null,
            nowEpochMillis = 1_000L,
        )

        assertIs<BackendAuthorizationCallbackDecision.MissingCode>(decision)
    }

    private fun pending(
        expiresAtEpochMillis: Long = 2_000L,
        serverUrl: String = "https://auth.example.test",
    ) = PendingBackendAuthorization(
        state = "state",
        expiresAtEpochMillis = expiresAtEpochMillis,
        serverUrl = serverUrl,
    )
}
