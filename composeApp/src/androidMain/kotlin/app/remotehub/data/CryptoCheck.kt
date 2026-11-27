package app.remotehub.data

import app.remotehub.diag.Diagnostics
import java.util.Base64

/**
 * Proves the secret box actually works on this device before a server is created.
 *
 * GitHub repository secrets must be sealed with libsodium's crypto_box_seal, which
 * lazysodium reaches through JNA. Both native libraries have to be inside the APK
 * for the right ABI: when JNA's dispatch library is missing the failure only shows
 * up at the "register secrets" step, after a repository has already been created.
 * This check runs once at startup so the problem is reported on the checklist
 * instead, with the exact reason in the log.
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
        if (checked) return failure
        val problem = try {
            val box = createSecretBox()
            // A throwaway recipient key: this only exercises the code path.
            val publicKey = Base64.getEncoder().encodeToString(randomBytes(32))
            val sealed = box.seal(PROBE, publicKey)
            val raw = Base64.getDecoder().decode(sealed)
            // crypto_box_seal prepends a 32-byte ephemeral public key and a 16-byte MAC.
            val expected = 48 + PROBE.toByteArray().size
            if (raw.size != expected) {
                "sealed box returned ${raw.size} bytes, expected $expected"
            } else {
                Diagnostics.info("crypto", "secret box self-test passed (sealed $expected bytes)")
                null
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
