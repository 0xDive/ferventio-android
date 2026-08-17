package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchAuthenticationFailureTest {
    @Test
    fun recognizesRejectedSubscriptionThroughWrappedCause() {
        val error = IllegalStateException(
            "bootstrap failed",
            TwitchEventSubSubscriptionException(
                statusCode = 401,
                twitchMessage = "Unauthorized",
            ),
        )

        assertTrue(error.isTwitchAuthenticationFailure())
    }

    @Test
    fun recognizesForbiddenSubscription() {
        assertTrue(
            TwitchEventSubSubscriptionException(
                statusCode = 403,
                twitchMessage = "Forbidden",
            ).isTwitchAuthenticationFailure(),
        )
    }

    @Test
    fun doesNotTreatRateLimitAsAuthenticationFailure() {
        assertFalse(
            TwitchEventSubSubscriptionException(
                statusCode = 429,
                twitchMessage = "Too Many Requests",
            ).isTwitchAuthenticationFailure(),
        )
    }
}
