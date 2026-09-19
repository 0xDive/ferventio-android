package io.ferventio.app.security

import io.ferventio.shared.security.JsonInputGuard as SharedJsonInputGuard

/** Compatibility adapter while Android callers remain in the app module. */
internal object JsonInputGuard {
    fun requireWithinLimits(
        raw: String,
        maxChars: Int,
        maxNestingDepth: Int,
        inputName: String,
    ) = SharedJsonInputGuard.requireWithinLimits(
        raw = raw,
        maxChars = maxChars,
        maxNestingDepth = maxNestingDepth,
        inputName = inputName,
    )
}
