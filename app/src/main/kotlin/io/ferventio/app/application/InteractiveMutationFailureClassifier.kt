package io.ferventio.app.application

import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.twitch.TwitchInteractiveApiException
import java.io.IOException

data class InteractiveMutationFailure(
    val kind: InteractiveMutationFailureKind,
    val recovery: InteractiveMutationRecovery,
)

object InteractiveMutationFailureClassifier {
    fun classify(error: Throwable): InteractiveMutationFailure {
        val apiError = error.findCause<TwitchInteractiveApiException>()
        if (apiError != null) {
            return when (apiError.statusCode) {
                401 -> failure(InteractiveMutationFailureKind.AUTHENTICATION, InteractiveMutationRecovery.RETRY)
                403 -> failure(InteractiveMutationFailureKind.PERMISSION)
                408 -> failure(InteractiveMutationFailureKind.NETWORK, InteractiveMutationRecovery.REFRESH)
                429 -> failure(InteractiveMutationFailureKind.RATE_LIMITED, InteractiveMutationRecovery.RETRY)
                400, 404, 409, 410, 422 -> failure(InteractiveMutationFailureKind.CONFLICT)
                in 500..599 -> failure(InteractiveMutationFailureKind.SERVER, InteractiveMutationRecovery.REFRESH)
                else -> failure(InteractiveMutationFailureKind.UNKNOWN)
            }
        }
        if (error.findCause<IOException>() != null) {
            return failure(InteractiveMutationFailureKind.NETWORK, InteractiveMutationRecovery.REFRESH)
        }
        return failure(InteractiveMutationFailureKind.UNKNOWN)
    }

    private fun failure(
        kind: InteractiveMutationFailureKind,
        recovery: InteractiveMutationRecovery = InteractiveMutationRecovery.NONE,
    ) = InteractiveMutationFailure(kind, recovery)

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}
