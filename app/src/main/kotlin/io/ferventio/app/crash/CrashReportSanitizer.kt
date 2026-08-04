package io.ferventio.app.crash

import io.ferventio.app.security.SensitiveDataRedactor

internal object CrashReportSanitizer {
    fun sanitize(component: String, error: Throwable): Throwable {
        val safeComponent = SensitiveDataRedactor.redact(component).orEmpty().take(MAX_COMPONENT_LENGTH)
        val safeMessage = SensitiveDataRedactor.redact(error.message).orEmpty().take(MAX_MESSAGE_LENGTH)
        val originalType = error::class.java.name.take(MAX_TYPE_LENGTH)
        return SanitizedNonFatalException(
            message = buildString {
                append('[').append(safeComponent).append("] ")
                append(originalType)
                if (safeMessage.isNotBlank()) append(": ").append(safeMessage)
            },
        ).apply {
            stackTrace = error.stackTrace
        }
    }

    private const val MAX_COMPONENT_LENGTH = 128
    private const val MAX_TYPE_LENGTH = 256
    private const val MAX_MESSAGE_LENGTH = 1_024
}

private class SanitizedNonFatalException(message: String) : RuntimeException(message)
