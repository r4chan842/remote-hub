package app.remotehub.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.remotehub.platform.AndroidEnv
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private class KeystoreSecureStore : SecureStore {
    private val prefs = AndroidEnv.appContext.getSharedPreferences("remotehub_vault", 0)
    private val alias = "remotehub.vault.key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun put(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + body
        prefs.edit().putString(key, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    override fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val payload = Base64.decode(raw, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, 12)
            val body = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

actual fun createSecureStore(): SecureStore = KeystoreSecureStore()
