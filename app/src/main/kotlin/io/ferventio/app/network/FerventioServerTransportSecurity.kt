package io.ferventio.app.network

import io.ferventio.app.BuildConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.CertificatePinner

internal object FerventioServerTransportSecurity {
    private val policy: ServerCertificatePinPolicy by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ServerCertificatePinPolicy.parse(BuildConfig.FERVENTIO_SERVER_CERTIFICATE_PINS)
    }

    private val certificatePinner: CertificatePinner? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (policy.isEmpty) return@lazy null
        CertificatePinner.Builder().apply {
            policy.entries().forEach { (host, pins) ->
                add(host, *pins.toTypedArray())
            }
        }.build()
    }

    fun configure(engine: OkHttpConfig) {
        val configuredPinner = certificatePinner ?: return
        engine.config {
            certificatePinner(configuredPinner)
        }
    }

    fun validateServerUrl(value: String): ValidatedFerventioServerUrl =
        policy.validateServerUrl(
            value = value,
            requirePinning = BuildConfig.REQUIRE_FERVENTIO_SERVER_PINNING,
            allowLocalCleartext = BuildConfig.DEBUG,
        )

    fun isPinned(value: String): Boolean =
        runCatching { validateServerUrl(value).pinned }.getOrDefault(false)
}
