package io.ferventio.app.data

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Versioned plaintext payload encrypted by [SecureTokenStore]. The Twitch refresh token is
 * deliberately absent: only the backend session and the current access token may be cached on the
 * device so an already-authorized user can continue working during a temporary backend outage.
 */
internal object AuthSessionPayloadCodec {
    private const val MAGIC = "FERVAUTH"
    private const val VERSION = 2
    private const val LEGACY_VERSION = "backend-session-v1"
    private const val MAX_SCOPES = 128

    data class StoredAuthentication(
        val backendCredential: BackendSessionCredential,
        val accessLease: TwitchAccessLease?,
    )

    fun encode(
        backendCredential: BackendSessionCredential,
        accessLease: TwitchAccessLease?,
    ): ByteArray {
        validateBackendCredential(backendCredential)
        accessLease?.let {
            validateAccessLease(it)
            require(it.backendSessionExpiresAtEpochMillis == backendCredential.expiresAtEpochMillis) {
                "Срок access-token cache не совпадает с серверной сессией"
            }
        }
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeUTF(MAGIC)
            output.writeInt(VERSION)
            output.writeLong(backendCredential.expiresAtEpochMillis)
            output.writeUTF(backendCredential.serverUrl)
            output.writeUTF(backendCredential.token)
            output.writeBoolean(accessLease != null)
            accessLease?.let { lease ->
                output.writeUTF(lease.accessToken)
                output.writeLong(lease.leaseExpiresAtEpochMillis)
                output.writeLong(lease.twitchExpiresAtEpochMillis)
                output.writeLong(lease.twitchValidatedAtEpochMillis)
                output.writeLong(lease.backendSessionExpiresAtEpochMillis)
                output.writeUTF(lease.session.clientId)
                output.writeUTF(lease.session.userId)
                output.writeUTF(lease.session.login)
                val scopes = lease.session.scopes.toList().sorted()
                output.writeInt(scopes.size)
                scopes.forEach(output::writeUTF)
                output.writeLong(lease.session.expiresInSeconds)
            }
        }
        return buffer.toByteArray()
    }

    fun decode(payload: ByteArray): StoredAuthentication {
        if (payload.startsWithLegacyPayload()) return decodeLegacy(payload)
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readUTF() == MAGIC) { "Неизвестный формат OAuth-хранилища" }
            require(input.readInt() == VERSION) { "Неподдерживаемая версия OAuth-хранилища" }
            val credential = BackendSessionCredential(
                expiresAtEpochMillis = input.readLong(),
                serverUrl = input.readUTF(),
                token = input.readUTF(),
            )
            validateBackendCredential(credential)
            val lease = if (input.readBoolean()) {
                val accessToken = input.readUTF()
                val leaseExpiresAt = input.readLong()
                val twitchExpiresAt = input.readLong()
                val twitchValidatedAt = input.readLong()
                val backendSessionExpiresAt = input.readLong()
                val clientId = input.readUTF()
                val userId = input.readUTF()
                val login = input.readUTF()
                val scopeCount = input.readInt()
                require(scopeCount in 0..MAX_SCOPES) { "Некорректное количество OAuth scopes" }
                val scopes = buildSet(scopeCount) {
                    repeat(scopeCount) { add(input.readUTF()) }
                }
                TwitchAccessLease(
                    accessToken = accessToken,
                    leaseExpiresAtEpochMillis = leaseExpiresAt,
                    twitchExpiresAtEpochMillis = twitchExpiresAt,
                    twitchValidatedAtEpochMillis = twitchValidatedAt,
                    backendSessionExpiresAtEpochMillis = backendSessionExpiresAt,
                    session = TwitchSession(
                        clientId = clientId,
                        userId = userId,
                        login = login,
                        scopes = scopes,
                        expiresInSeconds = input.readLong(),
                    ),
                ).also(::validateAccessLease)
            } else {
                null
            }
            require(input.available() == 0) { "OAuth-хранилище содержит лишние данные" }
            require(lease == null || lease.backendSessionExpiresAtEpochMillis == credential.expiresAtEpochMillis) {
                "Срок access-token cache не совпадает с серверной сессией"
            }
            return StoredAuthentication(credential, lease)
        }
    }

    private fun decodeLegacy(payload: ByteArray): StoredAuthentication {
        val parts = payload.toString(Charsets.UTF_8).split('\n', limit = 4)
        require(parts.size == 4 && parts[0] == LEGACY_VERSION) {
            "Некорректное прежнее OAuth-хранилище"
        }
        val credential = BackendSessionCredential(
            expiresAtEpochMillis = parts[1].toLong(),
            serverUrl = parts[2],
            token = parts[3],
        )
        validateBackendCredential(credential)
        return StoredAuthentication(credential, accessLease = null)
    }

    private fun ByteArray.startsWithLegacyPayload(): Boolean {
        val prefix = "$LEGACY_VERSION\n".toByteArray(Charsets.UTF_8)
        if (size < prefix.size) return false
        return prefix.indices.all { index -> this[index] == prefix[index] }
    }

    private fun validateBackendCredential(credential: BackendSessionCredential) {
        require(credential.serverUrl.isNotBlank()) { "Пустой адрес сервера" }
        require(credential.token.isNotBlank()) { "Пустая серверная сессия" }
        require(credential.expiresAtEpochMillis > 0L) { "Не задан срок серверной сессии" }
    }

    private fun validateAccessLease(lease: TwitchAccessLease) {
        require(lease.accessToken.isNotBlank()) { "Пустой Twitch access token" }
        require(lease.leaseExpiresAtEpochMillis > 0L) { "Не задан срок token lease" }
        require(lease.twitchExpiresAtEpochMillis > 0L) { "Не задан срок Twitch access token" }
        require(lease.leaseExpiresAtEpochMillis <= lease.twitchExpiresAtEpochMillis) {
            "Short lease не может истекать позже Twitch access token"
        }
        require(lease.twitchValidatedAtEpochMillis > 0L) { "Не задан момент проверки Twitch access token" }
        require(lease.twitchValidatedAtEpochMillis <= lease.twitchExpiresAtEpochMillis) {
            "Проверка Twitch access token не может быть позже его expiry"
        }
        require(lease.backendSessionExpiresAtEpochMillis > 0L) { "Не задан срок серверной сессии" }
        require(lease.session.clientId.isNotBlank()) { "Пустой Twitch Client ID" }
        require(lease.session.userId.isNotBlank()) { "Пустой Twitch user ID" }
        require(lease.session.login.isNotBlank()) { "Пустой Twitch login" }
        require(lease.session.scopes.size <= MAX_SCOPES) { "Слишком много OAuth scopes" }
        require(lease.session.scopes.none(String::isBlank)) { "Пустой OAuth scope" }
        require(lease.session.expiresInSeconds >= 0L) { "Некорректный срок Twitch access token" }
    }
}
