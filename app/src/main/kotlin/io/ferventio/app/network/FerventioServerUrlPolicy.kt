package io.ferventio.app.network

import io.ferventio.app.BuildConfig
import java.net.IDN
import java.net.URI
import java.util.Locale

internal data class ValidatedFerventioServerUrl(
    val baseUrl: String,
    val host: String,
)

/**
 * Validates the Ferventio server URL embedded in the application.
 *
 * Release builds require HTTPS. Debug builds additionally allow cleartext
 * connections to local emulator/development hosts.
 */
internal object FerventioServerUrlPolicy {
    private val localDebugHosts = setOf(
        "10.0.2.2",
        "localhost",
        "127.0.0.1",
    )

    fun validate(value: String): ValidatedFerventioServerUrl {
        val normalized = value
            .trim()
            .trimEnd('/')

        require(normalized.isNotBlank()) {
            "Не указан адрес сервера Ferventio"
        }

        val uri = runCatching {
            URI(normalized)
        }.getOrElse {
            throw IllegalArgumentException(
                "Некорректный адрес сервера Ferventio",
                it,
            )
        }

        val host = uri.host
            ?.let(::normalizeHost)
            .orEmpty()

        require(host.isNotBlank()) {
            "В адресе сервера Ferventio отсутствует host"
        }

        require(
            uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        ) {
            "Укажи базовый адрес сервера без логина, query и fragment"
        }

        val secure = uri.scheme.equals(
            "https",
            ignoreCase = true,
        )

        val localDebugConnection =
            BuildConfig.DEBUG &&
                uri.scheme.equals(
                    "http",
                    ignoreCase = true,
                ) &&
                host in localDebugHosts

        require(secure || localDebugConnection) {
            "Сервер Ferventio должен использовать HTTPS"
        }

        return ValidatedFerventioServerUrl(
            baseUrl = normalized,
            host = host,
        )
    }

    private fun normalizeHost(value: String): String {
        val host = value
            .trim()
            .lowercase(Locale.ROOT)

        require(host.isNotBlank()) {
            "Host сервера Ferventio не может быть пустым"
        }

        return runCatching {
            IDN.toASCII(
                host,
                IDN.USE_STD3_ASCII_RULES,
            )
        }.getOrElse {
            throw IllegalArgumentException(
                "Некорректный host сервера Ferventio: $host",
                it,
            )
        }
    }
}
