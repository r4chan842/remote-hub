package app.remotehub.data

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private class FileSecureStore : SecureStore {
    private val dir = File(System.getProperty("user.home"), ".remotehub").apply { mkdirs() }
    private val keyFile = File(dir, "master.key")
    private val dataFile = File(dir, "vault.bin")
    private val master: SecretKeySpec by lazy {
        if (!keyFile.exists()) {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.writeBytes(raw)
        }
        SecretKeySpec(keyFile.readBytes(), "AES")
    }
    private val map = LinkedHashMap<String, String>()

    init { load() }

    private fun load() {
        if (!dataFile.exists()) return
        runCatching {
            val blob = dataFile.readBytes()
            val iv = blob.copyOfRange(0, 12)
            val body = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, iv))
            val text = String(cipher.doFinal(body), Charsets.UTF_8)
            text.lines().forEach { line ->
                val idx = line.indexOf('\t')
                if (idx > 0) {
                    map[line.substring(0, idx)] = String(
                        Base64.getDecoder().decode(line.substring(idx + 1)),
                        Charsets.UTF_8,
                    )
                }
            }
        }
    }

    private fun persist() {
        val text = map.entries.joinToString("\n") { (k, v) ->
            "$k\t" + Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8))
        }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master, GCMParameterSpec(128, iv))
        dataFile.writeBytes(iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    override fun put(key: String, value: String) { map[key] = value; persist() }
    override fun get(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key); persist() }
    override fun clear() { map.clear(); persist() }
}

actual fun createSecureStore(): SecureStore = FileSecureStore()
