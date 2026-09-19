package io.ferventio.app.domain

data class InteractiveMutationFailureDisposition(
    val kind: InteractiveMutationFailureKind,
    val recovery: InteractiveMutationRecovery,
)

/**
 * Platform-neutral interpretation of Twitch mutation HTTP responses.
 *
 * Transport-specific exception decoding stays in the platform adapter; this policy only decides
 * which product failure state and recovery affordance correspond to a known HTTP status.
 */
object InteractiveMutationFailurePolicy {
    fun classifyHttpStatus(statusCode: Int): InteractiveMutationFailureDisposition = when (statusCode) {
        401 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.AUTHENTICATION,
            recovery = InteractiveMutationRecovery.RETRY,
        )

        403 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.PERMISSION,
            recovery = InteractiveMutationRecovery.NONE,
        )

        408 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.NETWORK,
            recovery = InteractiveMutationRecovery.REFRESH,
        )

        429 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.RATE_LIMITED,
            recovery = InteractiveMutationRecovery.RETRY,
        )

        400, 404, 409, 410, 422 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.CONFLICT,
            recovery = InteractiveMutationRecovery.NONE,
        )

        in 500..599 -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.SERVER,
            recovery = InteractiveMutationRecovery.REFRESH,
        )

        else -> InteractiveMutationFailureDisposition(
            kind = InteractiveMutationFailureKind.UNKNOWN,
            recovery = InteractiveMutationRecovery.NONE,
        )
    }
}
