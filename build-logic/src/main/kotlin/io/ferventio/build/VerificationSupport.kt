package io.ferventio.build

import org.gradle.api.GradleException
import java.net.URI

internal fun requireConfigured(name: String, value: String): String {
    val normalized = value.trim()
    if (
        normalized.isEmpty() ||
        normalized.equals("unconfigured", ignoreCase = true) ||
        normalized.contains("placeholder", ignoreCase = true) ||
        normalized.contains("change-me", ignoreCase = true)
    ) {
        throw GradleException("$name is not configured.")
    }
    return normalized
}

internal fun requireHttpsUrl(name: String, value: String): URI {
    val configured = requireConfigured(name, value)
    val uri = runCatching { URI(configured) }
        .getOrElse { throw GradleException("$name must be a valid HTTPS URL.", it) }
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
        throw GradleException("$name must be an absolute HTTPS URL.")
    }
    return uri
}
