package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class InteractiveMutationFailurePolicyTest {
    @Test
    fun `maps authentication and permission statuses`() {
        assertDisposition(401, InteractiveMutationFailureKind.AUTHENTICATION, InteractiveMutationRecovery.RETRY)
        assertDisposition(403, InteractiveMutationFailureKind.PERMISSION, InteractiveMutationRecovery.NONE)
    }

    @Test
    fun `maps transient network and rate limit statuses`() {
        assertDisposition(408, InteractiveMutationFailureKind.NETWORK, InteractiveMutationRecovery.REFRESH)
        assertDisposition(429, InteractiveMutationFailureKind.RATE_LIMITED, InteractiveMutationRecovery.RETRY)
    }

    @Test
    fun `maps conflict statuses without automatic recovery`() {
        listOf(400, 404, 409, 410, 422).forEach { statusCode ->
            assertDisposition(statusCode, InteractiveMutationFailureKind.CONFLICT, InteractiveMutationRecovery.NONE)
        }
    }

    @Test
    fun `maps server failures to refresh recovery`() {
        listOf(500, 503, 599).forEach { statusCode ->
            assertDisposition(statusCode, InteractiveMutationFailureKind.SERVER, InteractiveMutationRecovery.REFRESH)
        }
    }

    @Test
    fun `leaves unknown statuses without recovery`() {
        assertDisposition(418, InteractiveMutationFailureKind.UNKNOWN, InteractiveMutationRecovery.NONE)
    }

    private fun assertDisposition(
        statusCode: Int,
        expectedKind: InteractiveMutationFailureKind,
        expectedRecovery: InteractiveMutationRecovery,
    ) {
        val result = InteractiveMutationFailurePolicy.classifyHttpStatus(statusCode)
        assertEquals(expectedKind, result.kind)
        assertEquals(expectedRecovery, result.recovery)
    }
}
