package app.remotehub.data

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.interfaces.Box
import java.security.SecureRandom
import java.util.Base64

private object Sodium {
    val instance: LazySodiumJava by lazy { LazySodiumJava(SodiumJava()) }
}

private class DesktopSecretBox : SecretBox {
    override fun seal(message: String, recipientPublicKeyBase64: String): String {
        val sodium = Sodium.instance
        val recipient = Base64.getDecoder().decode(recipientPublicKeyBase64.trim())
        require(recipient.size == Box.PUBLICKEYBYTES) {
            "repository public key must be ${Box.PUBLICKEYBYTES} bytes, got ${recipient.size}"
        }
        val plain = message.toByteArray(Charsets.UTF_8)
        val cipher = ByteArray(Box.SEALBYTES + plain.size)
        // Same contract as Android: GitHub wants base64 of the raw sealed box, while
        // cryptoBoxSealEasy() would return hex. Keep both platforms byte-identical.
        check(sodium.cryptoBoxSeal(cipher, plain, plain.size.toLong(), recipient)) {
            "crypto_box_seal failed"
        }
        return Base64.getEncoder().encodeToString(cipher)
    }
}

actual fun createSecretBox(): SecretBox = DesktopSecretBox()

actual fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
