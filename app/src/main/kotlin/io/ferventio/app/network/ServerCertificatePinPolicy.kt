package io.ferventio.app.network

import java.net.IDN
import java.net.URI
import java.util.Base64
import java.util.Locale

internal data class ValidatedFerventioServerUrl(
    val baseUrl: String,
    val host: String,
    val pinned: Boolean,
)

/**
 * Parses build-time SPKI pins and validates the Ferventio server URL embedded in the app build.
 *
 * Configuration format:
 * `api.example.com=sha256/BASE64;api.example.com=sha256/BACKUP_BASE64`
 *
 * Hosts are exact and intentionally do not support wildcards. Multiple pins for one host
 * allow certificate/key rotation without shipping an emergency app update.
 */
internal class ServerCertificatePinPolicy private constructor(
    private val pinsByHost: Map<String, Set<String>>,
) {
    val isEmpty: Boolean
        get() = pinsByHost.isEmpty()

    fun pinsForHost(host: String): Set<String> =
        pinsByHost[normalizeHost(host)].orEmpty()

    fun hasPinsForHost(host: String): Boolean = pinsForHost(host).isNotEmpty()

    fun entries(): Map<String, Set<String>> = pinsByHost

    fun validateServerUrl(
        value: String,
        requirePinning: Boolean,
        allowLocalCleartext: Boolean,
    ): ValidatedFerventioServerUrl {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "Не указан адрес сервера Ferventio" }

        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("Некорректный адрес сервера Ferventio", it) }
        val host = uri.host?.let(::normalizeHost).orEmpty()
        require(host.isNotBlank()) { "В адресе сервера Ferventio отсутствует host" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Укажи базовый адрес сервера без логина, query и fragment"
        }

        val secure = uri.scheme.equals("https", ignoreCase = true)
        val localCleartext = allowLocalCleartext &&
            uri.scheme.equals("http", ignoreCase = true) &&
            host in LOCAL_DEBUG_HOSTS
        require(secure || localCleartext) {
            "Сервер Ferventio должен использовать HTTPS"
        }

        val pinned = secure && hasPinsForHost(host)
        require(!requirePinning || pinned) {
            "Для сервера $host нет certificate pin в этой release-сборке"
        }

        return ValidatedFerventioServerUrl(
            baseUrl = normalized,
            host = host,
            pinned = pinned,
        )
    }

    companion object {
        private val LOCAL_DEBUG_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

        fun parse(raw: String): ServerCertificatePinPolicy {
            if (raw.isBlank()) return ServerCertificatePinPolicy(emptyMap())

            val pins = linkedMapOf<String, LinkedHashSet<String>>()
            raw.split(';', '\n')
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { entry ->
                    val separator = entry.indexOf('=')
                    require(separator > 0 && separator < entry.lastIndex) {
                        "Некорректная запись certificate pin: ожидается host=sha256/base64"
                    }
                    val host = normalizeHost(entry.substring(0, separator))
                    require('*' !in host) {
                        "Wildcard certificate pins запрещены; укажи точный host"
                    }
                    val pin = validatePin(entry.substring(separator + 1).trim())
                    pins.getOrPut(host) { linkedSetOf() }.add(pin)
                }

            return ServerCertificatePinPolicy(
                pinsByHost = pins.mapValues { (_, values) -> values.toSet() },
            )
        }

        private fun validatePin(value: String): String {
            require(value.startsWith("sha256/")) {
                "Certificate pin должен использовать sha256/SPKI"
            }
            val decoded = runCatching {
                Base64.getDecoder().decode(value.removePrefix("sha256/"))
            }.getOrElse {
                throw IllegalArgumentException("Certificate pin содержит некорректный Base64", it)
            }
            require(decoded.size == 32) {
                "SHA-256 certificate pin должен декодироваться ровно в 32 байта"
            }
            return value
        }

        private fun normalizeHost(value: String): String {
            val host = value.trim().lowercase(Locale.ROOT)
            require(host.isNotBlank()) { "Certificate pin host не может быть пустым" }
            require("//" !in host && '/' !in host && ':' !in host && '@' !in host) {
                "Certificate pin host должен быть указан без scheme, port и path"
            }
            return runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }
                .getOrElse { throw IllegalArgumentException("Некорректный certificate pin host: $host", it) }
        }
    }
}
