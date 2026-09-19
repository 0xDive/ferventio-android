package io.ferventio.app.domain

/**
 * Platform-neutral authentication snapshot restored from secure storage.
 * Platform codecs and secure-storage implementations own serialization and encryption.
 */
data class StoredAuthentication(
    val backendCredential: BackendSessionCredential,
    val accessLease: TwitchAccessLease?,
)
