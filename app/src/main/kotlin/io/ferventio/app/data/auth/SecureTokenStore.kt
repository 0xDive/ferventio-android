package io.ferventio.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.TwitchAccessLease
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun save(
        credential: BackendSessionCredential,
        accessLease: TwitchAccessLease?,
    ) {
        val payload = AuthSessionPayloadCodec.encode(credential, accessLease)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        check(
            preferences.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit(),
        ) { "Не удалось атомарно сохранить OAuth-сессию" }
        runCatching { keyStore.deleteEntry(LEGACY_KEY_ALIAS) }
    }

    fun load(): BackendSessionCredential? = loadAuthentication()?.backendCredential

    internal fun loadAuthentication(): AuthSessionPayloadCodec.StoredAuthentication? {
        val encryptedBase64 = preferences.getString(KEY_CIPHERTEXT, null)
        val ivBase64 = preferences.getString(KEY_IV, null)
        if (encryptedBase64 == null && ivBase64 == null) return null
        if (encryptedBase64 == null || ivBase64 == null) {
            clearAndDeleteKeys()
            return null
        }
        val key = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()
        if (key == null) {
            clearAndDeleteKeys()
            return null
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivBase64, Base64.NO_WRAP)),
            )
            AuthSessionPayloadCodec.decode(
                cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP)),
            )
        }.onFailure {
            clearAndDeleteKeys()
        }.getOrNull()?.takeIf {
            it.backendCredential.expiresAtEpochMillis > System.currentTimeMillis()
        } ?: run {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun clearAndDeleteKeys() {
        clear()
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        runCatching { keyStore.deleteEntry(LEGACY_KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }
            .getOrElse {
                runCatching { keyStore.deleteEntry(KEY_ALIAS) }
                null
            }
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "ferventio_token"
        const val KEY_ALIAS = "ferventio_backend_session_v1"
        const val LEGACY_KEY_ALIAS = "ferventio_twitch_oauth"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
