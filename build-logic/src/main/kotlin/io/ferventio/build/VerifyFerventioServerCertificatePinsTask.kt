package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.net.IDN
import java.util.Base64
import java.util.Locale

/**
 * Validates release-time host-to-SPKI mappings for the configured Ferventio backend.
 *
 * Expected format:
 * host.example.com=sha256/BASE64;host.example.com=sha256/BACKUP_BASE64
 */
abstract class VerifyFerventioServerCertificatePinsTask : DefaultTask() {
    @get:Input
    abstract val configuredDefaultPushServerUrl: Property<String>

    @get:Input
    abstract val configuredCertificatePins: Property<String>

    @TaskAction
    fun verifyPins() {
        val serverUri = requireHttpsUrl(
            "FERVENTIO_SERVER_URL",
            configuredDefaultPushServerUrl.get(),
        )
        val serverHost = normalizeHost(
            serverUri.host
                ?: throw GradleException(
                    "FERVENTIO_SERVER_URL must contain a host.",
                ),
        )

        val entries = requireConfigured(
            "FERVENTIO_SERVER_CERTIFICATE_PINS",
            configuredCertificatePins.get(),
        )
            .split(';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)

        if (entries.isEmpty()) {
            throw GradleException(
                "At least one Ferventio server certificate pin is required.",
            )
        }

        val configuredHosts = entries
            .mapIndexed { index, entry ->
                parseAndValidateEntry(
                    entry = entry,
                    position = index + 1,
                )
            }
            .toSet()

        if (serverHost !in configuredHosts) {
            throw GradleException(
                "FERVENTIO_SERVER_CERTIFICATE_PINS does not contain " +
                    "a certificate pin for $serverHost.",
            )
        }
    }

    private fun parseAndValidateEntry(
        entry: String,
        position: Int,
    ): String {
        val separator = entry.indexOf('=')

        if (separator <= 0 || separator >= entry.lastIndex) {
            throw GradleException(
                "Invalid certificate pin entry #$position. " +
                    "Expected host=sha256/<base64-digest>.",
            )
        }

        val host = normalizeHost(entry.substring(0, separator))

        if ('*' in host) {
            throw GradleException(
                "Wildcard certificate pin hosts are not allowed.",
            )
        }

        validatePin(
            value = entry.substring(separator + 1).trim(),
            position = position,
        )

        return host
    }

    private fun validatePin(
        value: String,
        position: Int,
    ) {
        if (!value.startsWith("sha256/")) {
            throw GradleException(
                "Certificate pin entry #$position must use sha256/SPKI.",
            )
        }

        val decoded = runCatching {
            Base64.getDecoder().decode(
                value.removePrefix("sha256/"),
            )
        }.getOrElse {
            throw GradleException(
                "Certificate pin entry #$position contains invalid Base64.",
                it,
            )
        }

        if (decoded.size != 32) {
            throw GradleException(
                "Certificate pin entry #$position must decode to 32 bytes.",
            )
        }
    }

    private fun normalizeHost(value: String): String {
        val host = value
            .trim()
            .lowercase(Locale.ROOT)

        if (
            host.isBlank() ||
            "//" in host ||
            '/' in host ||
            ':' in host ||
            '@' in host
        ) {
            throw GradleException(
                "Certificate pin host must not contain scheme, port or path.",
            )
        }

        return runCatching {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        }.getOrElse {
            throw GradleException(
                "Invalid certificate pin host: $host",
                it,
            )
        }
    }
}
