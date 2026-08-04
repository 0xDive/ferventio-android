package io.ferventio.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class DeviceSecretStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun getOrCreate(create: () -> String): String {
        preferences.getString(KEY_CIPHERTEXT, null)?.let { encoded ->
            runCatching { decrypt(encoded) }.getOrNull()?.takeIf(String::isNotBlank)?.let { return it }
        }
        val value = create().also { require(it.isNotBlank()) }
        check(
            preferences.edit()
                .putString(KEY_CIPHERTEXT, encrypt(value))
                .commit(),
        ) { "Не удалось сохранить device secret в Android Keystore" }
        return value
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + cipher.iv.size + ciphertext.size)
        combined[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(combined, destinationOffset = 1)
        ciphertext.copyInto(combined, destinationOffset = 1 + cipher.iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.isNotEmpty()) { "Пустой device secret" }
        val ivLength = combined[0].toInt() and 0xff
        require(ivLength in 12..32 && combined.size > 1 + ivLength) { "Повреждённый device secret" }
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
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
        const val FILE_NAME = "ferventio_device_credentials"
        const val KEY_CIPHERTEXT = "installation_secret_ciphertext"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ferventio_device_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
