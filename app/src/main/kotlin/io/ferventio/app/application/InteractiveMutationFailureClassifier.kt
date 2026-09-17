package io.ferventio.app.application

import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationFailurePolicy
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.twitch.TwitchInteractiveApiException
import java.io.IOException

internal data class InteractiveMutationFailure(
    val kind: InteractiveMutationFailureKind,
    val recovery: InteractiveMutationRecovery,
)

internal object InteractiveMutationFailureClassifier {
    fun classify(error: Throwable): InteractiveMutationFailure {
        val apiError = error.findCause<TwitchInteractiveApiException>()
        if (apiError != null) {
            val disposition = InteractiveMutationFailurePolicy.classifyHttpStatus(apiError.statusCode)
            return InteractiveMutationFailure(
                kind = disposition.kind,
                recovery = disposition.recovery,
            )
        }

        return if (error.findCause<IOException>() != null) {
            InteractiveMutationFailure(
                kind = InteractiveMutationFailureKind.NETWORK,
                recovery = InteractiveMutationRecovery.REFRESH,
            )
        } else {
            InteractiveMutationFailure(
                kind = InteractiveMutationFailureKind.UNKNOWN,
                recovery = InteractiveMutationRecovery.NONE,
            )
        }
    }
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
