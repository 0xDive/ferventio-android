package io.ferventio.app.security

import io.ferventio.shared.security.SensitiveDataRedactor as SharedSensitiveDataRedactor
import java.net.URI

/**
 * Android/JVM compatibility adapter around the shared redaction policy.
 * URL parsing stays here because it relies on java.net.URI.
 */
internal object SensitiveDataRedactor {
    const val REDACTED = SharedSensitiveDataRedactor.REDACTED

    fun redact(value: String?): String? = SharedSensitiveDataRedactor.redact(value)

    fun urlForLog(rawUrl: String): String = runCatching {
        val uri = URI(rawUrl)
        val authority = uri.rawAuthority?.substringAfterLast('@')
            ?: return@runCatching redact(rawUrl).orEmpty()
        buildString {
            uri.scheme?.let { append(it).append("://") }
            append(redact(authority).orEmpty())
            append(redact(uri.rawPath).orEmpty())
            if (uri.rawQuery != null) append("?<redacted>")
            if (uri.rawFragment != null) append("#<redacted>")
        }
    }.getOrElse { redact(rawUrl).orEmpty() }
}
