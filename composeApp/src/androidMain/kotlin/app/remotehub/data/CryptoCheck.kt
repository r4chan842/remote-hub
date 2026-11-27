package app.remotehub.data

import app.remotehub.diag.Diagnostics
import com.goterl.lazysodium.interfaces.Box
import java.util.Base64

/**
 * Proves the whole secret path works on this device before a server is created.
 *
 * It is a real round trip through the shipped native libraries: libsodium generates
 * a key pair, the app seals a probe exactly the way a GitHub repository secret is
 * sealed (base64 of the raw sealed box), and the probe is opened again with the
 * recipient's private key. A wrong encoding, a missing JNA dispatch library or a
 * broken libsodium all fail here, on the checklist, instead of halfway through
 * creating a repository.
 */
object CryptoCheck {
    private const val PROBE = "remotehub-crypto-check"

    @Volatile
    private var checked = false

    /** Null when sealing works; otherwise the technical reason. */
    @Volatile
    var failure: String? = null
        private set

    fun run(): String? {
        // A pass is cached for the process; a failure is retried on the next attempt.
        if (checked && failure == null) return null
        val problem = try {
            val sodium = Sodium.instance
            val pair = sodium.cryptoBoxKeypair()
            val publicBase64 = Base64.getEncoder().encodeToString(pair.publicKey.asBytes)
            val sealed = createSecretBox().seal(PROBE, publicBase64)

            val raw = Base64.getDecoder().decode(sealed)
            val expected = Box.SEALBYTES + PROBE.toByteArray(Charsets.UTF_8).size
            if (raw.size != expected) {
                "sealed box is ${raw.size} bytes, expected $expected (GitHub needs base64 of the raw box)"
            } else {
                val opened = ByteArray(raw.size - Box.SEALBYTES)
                val ok = sodium.cryptoBoxSealOpen(
                    opened, raw, raw.size.toLong(),
                    pair.publicKey.asBytes, pair.secretKey.asBytes,
                )
                if (!ok) {
                    "crypto_box_seal_open rejected the sealed box"
                } else if (opened.decodeToString() != PROBE) {
                    "sealed box round trip returned different text"
                } else {
                    Diagnostics.info("crypto", "secret box round trip passed ($expected bytes, base64 ${sealed.length} chars)")
                    null
                }
            }
        } catch (error: Throwable) {
            // UnsatisfiedLinkError is an Error, not an Exception: catch Throwable.
            val text = "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
            Diagnostics.error("crypto", "secret box self-test failed", error)
            text
        }
        failure = problem
        checked = true
        return problem
    }
}
