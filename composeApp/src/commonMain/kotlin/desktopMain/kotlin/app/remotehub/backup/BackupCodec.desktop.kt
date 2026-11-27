package app.remotehub.backup

import app.remotehub.data.randomBytes
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object BackupCodec {
    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, BackupFormat.PBKDF2_ROUNDS, BackupFormat.KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    actual fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = randomBytes(BackupFormat.SALT_SIZE)
        val nonce = randomBytes(BackupFormat.NONCE_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(BackupFormat.TAG_BITS, nonce))
        cipher.updateAAD(BackupFormat.MAGIC.encodeToByteArray())
        val body = cipher.doFinal(plain)
        return BackupFormat.MAGIC.encodeToByteArray() + salt + nonce + body
    }

    actual fun decrypt(sealed: ByteArray, passphrase: String): ByteArray {
        val magicSize = BackupFormat.MAGIC.length
        require(sealed.size > magicSize + BackupFormat.SALT_SIZE + BackupFormat.NONCE_SIZE) { "backup too short" }
        val magic = sealed.copyOfRange(0, magicSize).decodeToString()
        require(magic == BackupFormat.MAGIC) { "unsupported backup format" }
        val saltStart = magicSize
        val nonceStart = saltStart + BackupFormat.SALT_SIZE
        val bodyStart = nonceStart + BackupFormat.NONCE_SIZE
        val salt = sealed.copyOfRange(saltStart, nonceStart)
        val nonce = sealed.copyOfRange(nonceStart, bodyStart)
        val body = sealed.copyOfRange(bodyStart, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(BackupFormat.TAG_BITS, nonce))
        cipher.updateAAD(BackupFormat.MAGIC.encodeToByteArray())
        return cipher.doFinal(body)
    }
}
