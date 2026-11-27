package app.remotehub

import app.remotehub.backup.BackupCodec
import app.remotehub.backup.BackupFormat
import app.remotehub.data.randomPassword
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCodecTest {
    @Test
    fun roundTripPreservesBytes() {
        val passphrase = randomPassword(32)
        val payload = "session snapshot with unicode: نشست ریموت ۱۲".encodeToByteArray()
        val sealed = BackupCodec.encrypt(payload, passphrase)
        val opened = BackupCodec.decrypt(sealed, passphrase)
        assertEquals(payload.decodeToString(), opened.decodeToString())
    }

    @Test
    fun ciphertextDoesNotLeakPlaintext() {
        val payload = "RDP_PASSWORD=hunter2".encodeToByteArray()
        val sealed = BackupCodec.encrypt(payload, "correct horse battery staple")
        assertFalse(sealed.decodeToString().contains("hunter2"))
        assertTrue(sealed.size > payload.size)
    }

    @Test
    fun headerCarriesMagicSaltAndNonce() {
        val sealed = BackupCodec.encrypt("x".encodeToByteArray(), "pass")
        assertEquals(BackupFormat.MAGIC, sealed.copyOfRange(0, 4).decodeToString())
        assertTrue(sealed.size >= 4 + BackupFormat.SALT_SIZE + BackupFormat.NONCE_SIZE + 16)
    }

    @Test
    fun wrongPassphraseFailsAuthentication() {
        val sealed = BackupCodec.encrypt("secret".encodeToByteArray(), "right")
        assertFailsWith<AEADBadTagException> { BackupCodec.decrypt(sealed, "wrong") }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = BackupCodec.encrypt("secret".encodeToByteArray(), "pass")
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<AEADBadTagException> { BackupCodec.decrypt(sealed, "pass") }
    }

    @Test
    fun truncatedInputIsRejected() {
        assertFailsWith<IllegalArgumentException> { BackupCodec.decrypt(ByteArray(8), "pass") }
    }

    @Test
    fun foreignMagicIsRejected() {
        val sealed = BackupCodec.encrypt("secret".encodeToByteArray(), "pass")
        sealed[0] = 'X'.code.toByte()
        assertFailsWith<IllegalArgumentException> { BackupCodec.decrypt(sealed, "pass") }
    }

    @Test
    fun eachEncryptionUsesFreshSaltAndNonce() {
        val a = BackupCodec.encrypt("same".encodeToByteArray(), "pass")
        val b = BackupCodec.encrypt("same".encodeToByteArray(), "pass")
        assertFalse(a.contentEquals(b))
        assertEquals("same", BackupCodec.decrypt(a, "pass").decodeToString())
        assertEquals("same", BackupCodec.decrypt(b, "pass").decodeToString())
    }

    @Test
    fun largePayloadRoundTrips() {
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        val sealed = BackupCodec.encrypt(payload, "pass")
        assertTrue(BackupCodec.decrypt(sealed, "pass").contentEquals(payload))
    }
}
