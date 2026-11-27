package app.remotehub

import app.remotehub.data.createSecretBox
import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretBoxTest {
    private val sodium = LazySodiumJava(SodiumJava())

    @Test
    fun sealedValueOpensWithRecipientSecretKey() {
        val pair: KeyPair = sodium.cryptoBoxKeypair()
        val box = createSecretBox()
        val message = "tskey-auth-kExampleSecretValue123"
        val sealed = box.seal(message, pair.publicKey.asBase64String)
        val opened = sodium.cryptoBoxSealOpenEasy(sealed, pair.secretKey.asBase64String)
        assertEquals(message, opened)
    }

    @Test
    fun sealedValueIsBase64AndNonDeterministic() {
        val pair = sodium.cryptoBoxKeypair()
        val box = createSecretBox()
        val a = box.seal("same", pair.publicKey.asBase64String)
        val b = box.seal("same", pair.publicKey.asBase64String)
        assertNotEquals(a, b)
        assertTrue(a.none { it == '\n' || it == ' ' })
    }

    @Test
    fun unicodeSecretRoundTrips() {
        val pair = sodium.cryptoBoxKeypair()
        val box = createSecretBox()
        val message = "گذرواژهٔ فارسی با نویسه‌های خاص !@#"
        val sealed = box.seal(message, pair.publicKey.asBase64String)
        assertEquals(message, sodium.cryptoBoxSealOpenEasy(sealed, pair.secretKey.asBase64String))
    }

    @Test
    fun wrongRecipientCannotOpen() {
        val pair = sodium.cryptoBoxKeypair()
        val other = sodium.cryptoBoxKeypair()
        val sealed = createSecretBox().seal("secret", pair.publicKey.asBase64String)
        val opened = sodium.cryptoBoxSealOpenEasy(sealed, other.secretKey.asBase64String)
        assertTrue(opened.isNullOrEmpty())
    }

    @Test
    fun realGitHubKeyShapeIsAccepted() {
        val publicKey = Key.fromBase64String(sodium.cryptoBoxKeypair().publicKey.asBase64String)
        assertEquals(32, publicKey.asBytes.size)
        val sealed = createSecretBox().seal("value", publicKey.asBase64String)
        assertTrue(sealed.length > 44)
    }
}
