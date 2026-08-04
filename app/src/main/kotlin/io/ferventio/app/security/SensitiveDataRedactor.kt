package io.ferventio.app.security

import java.net.URI

/**
 * Removes credentials and short-lived authorization material before text leaves the process.
 * Keep this class free of Android dependencies so the rules can be covered by JVM tests.
 */
internal object SensitiveDataRedactor {
    const val REDACTED = "<redacted>"

    private val authorizationHeader = Regex(
        pattern = "(?i)(\\b(?:authorization|proxy-authorization)\\b\\s*[:=]\\s*(?:bearer|oauth)\\s+)([^\\s,;]+)",
    )
    private val bearerToken = Regex(
        pattern = "(?i)(\\b(?:bearer|oauth)\\s+)([A-Za-z0-9._~+/=-]{8,})",
    )
    private val ircOAuthToken = Regex(
        pattern = "(?i)(\\boauth:)([A-Za-z0-9._~+/=-]{8,})",
    )
    private val sensitiveQueryParameter = Regex(
        pattern = "(?i)([?&](?:access_token|refresh_token|id_token|token|session_token|sessionToken|device_secret|deviceSecret|client_secret|clientSecret|code|state)=)([^&#\\s]+)",
    )
    private val sensitiveJsonField = Regex(
        pattern = "(?i)([\"'](?:accessToken|refreshToken|idToken|sessionToken|deviceSecret|clientSecret|authorization|token|code|state)[\"']\\s*:\\s*[\"'])([^\"']*)([\"'])",
    )
    private val sensitiveAssignment = Regex(
        pattern = "(?i)(\\b(?:accessToken|refreshToken|idToken|sessionToken|deviceSecret|clientSecret|X-Device-Secret|Client-Secret)\\b\\s*[:=]\\s*)([^\\s,;]+)",
    )
    private val jwt = Regex(
        pattern = "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]{8,})?\\b",
    )
    private val urlUserInfo = Regex(
        pattern = "(://[^:/@\\s]+:)([^@/\\s]+)(@)",
    )

    fun redact(value: String?): String? {
        if (value == null || value.isEmpty()) return value
        return value
            .replace(authorizationHeader, "$1$REDACTED")
            .replace(bearerToken, "$1$REDACTED")
            .replace(ircOAuthToken, "$1$REDACTED")
            .replace(sensitiveQueryParameter, "$1$REDACTED")
            .replace(sensitiveJsonField, "$1$REDACTED$3")
            .replace(sensitiveAssignment, "$1$REDACTED")
            .replace(jwt, REDACTED)
            .replace(urlUserInfo, "$1$REDACTED$3")
    }

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
