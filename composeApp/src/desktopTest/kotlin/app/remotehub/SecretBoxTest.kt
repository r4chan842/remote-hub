package app.remotehub

import app.remotehub.data.createSecretBox
import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.utils.KeyPair
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies the exact contract GitHub requires for a repository secret:
 * `encrypted_value` must be **base64 of the raw crypto_box_seal output**.
 *
 * The previous version of this test opened the value with cryptoBoxSealOpenEasy,
 * which takes lazysodium's own hex encoding. That made a hex-producing seal look
 * correct while GitHub could never decrypt it. Everything here now decodes base64
 * first and opens the raw bytes, which is what GitHub's server does.
 */
class SecretBoxTest {
    private val sodium = LazySodiumJava(SodiumJava())

    private fun KeyPair.publicBase64(): String =
        Base64.getEncoder().encodeToString(publicKey.asBytes)

    /** Open a sealed value the way GitHub does: base64 decode, then crypto_box_seal_open. */
    private fun open(sealed: String, pair: KeyPair): String {
        val raw = Base64.getDecoder().decode(sealed)
        val plain = ByteArray(raw.size - Box.SEALBYTES)
        val ok = sodium.cryptoBoxSealOpen(
            plain, raw, raw.size.toLong(),
            pair.publicKey.asBytes, pair.secretKey.asBytes,
        )
        check(ok) { "crypto_box_seal_open failed" }
        return plain.decodeToString()
    }

    @Test
    fun sealedValueOpensWithRecipientSecretKey() {
        val pair = sodium.cryptoBoxKeypair()
        val message = "tskey-auth-kExampleSecretValue123"
        val sealed = createSecretBox().seal(message, pair.publicBase64())
        assertEquals(message, open(sealed, pair))
    }

    @Test
    fun sealedValueIsBase64OfTheRawBox() {
        val pair = sodium.cryptoBoxKeypair()
        val message = "same"
        val sealed = createSecretBox().seal(message, pair.publicBase64())
        val raw = Base64.getDecoder().decode(sealed)
        // Exactly 32 bytes of ephemeral key + 16 bytes of MAC + the message.
        assertEquals(Box.SEALBYTES + message.toByteArray().size, raw.size)
        // Re-encoding must reproduce the same string: no hex, no line breaks.
        assertEquals(sealed, Base64.getEncoder().encodeToString(raw))
        assertTrue(sealed.none { it == '\n' || it == ' ' })
    }

    @Test
    fun sealedValueIsNonDeterministic() {
        val pair = sodium.cryptoBoxKeypair()
        val box = createSecretBox()
        assertNotEquals(box.seal("same", pair.publicBase64()), box.seal("same", pair.publicBase64()))
    }

    @Test
    fun unicodeSecretRoundTrips() {
        val pair = sodium.cryptoBoxKeypair()
        val message = "گذرواژهٔ فارسی با نویسه‌های خاص !@#"
        val sealed = createSecretBox().seal(message, pair.publicBase64())
        assertEquals(message, open(sealed, pair))
    }

    @Test
    fun wrongRecipientCannotOpen() {
        val pair = sodium.cryptoBoxKeypair()
        val other = sodium.cryptoBoxKeypair()
        val sealed = createSecretBox().seal("secret", pair.publicBase64())
        val raw = Base64.getDecoder().decode(sealed)
        val plain = ByteArray(raw.size - Box.SEALBYTES)
        val ok = runCatching {
            sodium.cryptoBoxSealOpen(
                plain, raw, raw.size.toLong(),
                other.publicKey.asBytes, other.secretKey.asBytes,
            )
        }
        assertTrue(ok.isFailure || ok.getOrNull() == false)
    }

    @Test
    fun repositoryKeyShapeIsAccepted() {
        val publicKey = sodium.cryptoBoxKeypair().publicKey
        assertEquals(32, publicKey.asBytes.size)
        val base64 = Base64.getEncoder().encodeToString(publicKey.asBytes)
        assertEquals(44, base64.length)
        val sealed = createSecretBox().seal("value", base64)
        assertEquals(Box.SEALBYTES + 5, Base64.getDecoder().decode(sealed).size)
    }

    @Test
    fun longSecretRoundTrips() {
        val pair = sodium.cryptoBoxKeypair()
        val message = "x".repeat(4096)
        val sealed = createSecretBox().seal(message, pair.publicBase64())
        assertEquals(message, open(sealed, pair))
    }

    @Test
    fun generatedPasswordSurvivesTheSeal() {
        val pair = sodium.cryptoBoxKeypair()
        val password = app.remotehub.data.randomPassword(24)
        val sealed = createSecretBox().seal(password, pair.publicBase64())
        assertEquals(password, open(sealed, pair))
    }
}
