package io.ferventio.app.security

import android.util.Log
import io.ferventio.app.crash.CrashReporter

/** Android logger that guarantees redaction for both messages and throwable text. */
internal object SafeLog {
    fun v(tag: String, message: String): Int = write(Log.VERBOSE, tag, message)
    fun d(tag: String, message: String): Int = write(Log.DEBUG, tag, message)
    fun i(tag: String, message: String): Int = write(Log.INFO, tag, message)
    fun w(tag: String, message: String, error: Throwable? = null): Int =
        write(Log.WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null): Int =
        write(Log.ERROR, tag, message, error)

    private fun write(priority: Int, tag: String, message: String, error: Throwable? = null): Int {
        val safeMessage = SensitiveDataRedactor.redact(message).orEmpty()
        val safeErrorSummary = error?.let {
            val safeErrorMessage = SensitiveDataRedactor.redact(it.message).orEmpty()
            "${it::class.java.simpleName}: $safeErrorMessage"
        }
        CrashReporter.breadcrumb(
            priority = priority,
            tag = tag,
            message = listOfNotNull(safeMessage.takeIf(String::isNotBlank), safeErrorSummary)
                .joinToString(" | "),
        )
        val output = if (error == null) {
            safeMessage
        } else {
            val safeStackTrace = SensitiveDataRedactor.redact(error.stackTraceToString()).orEmpty()
            "$safeMessage\n$safeStackTrace"
        }
        // Logging must never alter application control flow. This also keeps pure JVM tests
        // independent from android.util.Log's non-functional local-unit-test stubs.
        return runCatching { Log.println(priority, tag, output) }.getOrDefault(0)
    }
}
