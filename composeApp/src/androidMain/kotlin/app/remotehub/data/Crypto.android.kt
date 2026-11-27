package app.remotehub.data

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import java.security.SecureRandom

private object Sodium {
    val instance: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }
}

private class AndroidSecretBox : SecretBox {
    override fun seal(message: String, recipientPublicKeyBase64: String): String =
        Sodium.instance.cryptoBoxSealEasy(message, Key.fromBase64String(recipientPublicKeyBase64))
}

actual fun createSecretBox(): SecretBox = AndroidSecretBox()

actual fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
