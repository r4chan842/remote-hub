package app.remotehub.data

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.Key
import java.security.SecureRandom

private object Sodium {
    val instance: LazySodiumJava by lazy { LazySodiumJava(SodiumJava()) }
}

private class DesktopSecretBox : SecretBox {
    override fun seal(message: String, recipientPublicKeyBase64: String): String =
        Sodium.instance.cryptoBoxSealEasy(message, Key.fromBase64String(recipientPublicKeyBase64))
}

actual fun createSecretBox(): SecretBox = DesktopSecretBox()

actual fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
