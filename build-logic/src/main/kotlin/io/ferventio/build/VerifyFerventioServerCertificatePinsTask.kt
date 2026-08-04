package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Validates release-time SPKI pins for the configured Ferventio backend. */
abstract class VerifyFerventioServerCertificatePinsTask : DefaultTask() {
    @get:Input abstract val configuredDefaultPushServerUrl: Property<String>
    @get:Input abstract val configuredCertificatePins: Property<String>

    @TaskAction
    fun verifyPins() {
        requireHttpsUrl(
            "FERVENTIO_SERVER_URL",
            configuredDefaultPushServerUrl.get(),
        )
        val pins = requireConfigured(
            "FERVENTIO_SERVER_CERTIFICATE_PINS",
            configuredCertificatePins.get(),
        )
            .split(',', ';', '\n', '\r', ' ', '\t')
            .map(String::trim)
            .filter(String::isNotEmpty)

        if (pins.isEmpty()) {
            throw GradleException("At least one Ferventio server certificate pin is required.")
        }

        val invalid = pins.filterNot(SPKI_PIN_PATTERN::matches)
        if (invalid.isNotEmpty()) {
            throw GradleException(
                "Invalid SPKI pin(s): ${invalid.joinToString()}. " +
                    "Expected values in sha256/<base64-digest> format.",
            )
        }
    }

    private companion object {
        val SPKI_PIN_PATTERN = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
    }
}
