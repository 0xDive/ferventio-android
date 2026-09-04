package io.ferventio.app.domain

/**
 * Fail-closed structural validation for authentication state before it is persisted or restored.
 * Serialization and platform-specific secure storage remain outside the domain layer.
 */
object AuthenticationPersistenceValidation {
    const val MAX_SCOPES = 128

    fun requireValid(
        backendCredential: BackendSessionCredential,
        accessLease: TwitchAccessLease?,
    ) {
        requireValidBackendCredential(backendCredential)
        accessLease?.let { lease ->
            requireValidAccessLease(lease)
            require(lease.backendSessionExpiresAtEpochMillis == backendCredential.expiresAtEpochMillis) {
                "Срок access-token cache не совпадает с серверной сессией"
            }
        }
    }

    fun requireValidBackendCredential(credential: BackendSessionCredential) {
        require(credential.serverUrl.isNotBlank()) { "Пустой адрес сервера" }
        require(credential.token.isNotBlank()) { "Пустая серверная сессия" }
        require(credential.expiresAtEpochMillis > 0L) { "Не задан срок серверной сессии" }
    }

    fun requireValidAccessLease(lease: TwitchAccessLease) {
        require(lease.accessToken.isNotBlank()) { "Пустой Twitch access token" }
        require(lease.leaseExpiresAtEpochMillis > 0L) { "Не задан срок token lease" }
        require(lease.twitchExpiresAtEpochMillis > 0L) { "Не задан срок Twitch access token" }
        require(lease.leaseExpiresAtEpochMillis <= lease.twitchExpiresAtEpochMillis) {
            "Short lease не может истекать позже Twitch access token"
        }
        require(lease.twitchValidatedAtEpochMillis > 0L) {
            "Не задан момент проверки Twitch access token"
        }
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
