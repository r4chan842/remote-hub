package app.remotehub.data

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import java.security.SecureRandom
import java.util.Base64

internal object Sodium {
    val instance: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }
}

private class AndroidSecretBox : SecretBox {
    override fun seal(message: String, recipientPublicKeyBase64: String): String {
        val sodium = Sodium.instance
        val recipient = Base64.getDecoder().decode(recipientPublicKeyBase64.trim())
        require(recipient.size == Box.PUBLICKEYBYTES) {
            "repository public key must be ${Box.PUBLICKEYBYTES} bytes, got ${recipient.size}"
        }
        val plain = message.toByteArray(Charsets.UTF_8)
        val cipher = ByteArray(Box.SEALBYTES + plain.size)
        // Use the byte API on purpose. cryptoBoxSealEasy() returns HEX, and GitHub
        // expects base64 of the raw sealed box, so the convenience method silently
        // produces a value GitHub cannot decrypt. Encoding is done here instead.
        check(sodium.cryptoBoxSeal(cipher, plain, plain.size.toLong(), recipient)) {
            "crypto_box_seal failed"
        }
        return Base64.getEncoder().encodeToString(cipher)
    }
}

actual fun createSecretBox(): SecretBox = AndroidSecretBox()

actual fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
