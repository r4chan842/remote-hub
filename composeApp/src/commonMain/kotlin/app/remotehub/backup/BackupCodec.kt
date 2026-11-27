package app.remotehub.backup

expect object BackupCodec {
    fun encrypt(plain: ByteArray, passphrase: String): ByteArray
    fun decrypt(sealed: ByteArray, passphrase: String): ByteArray
}

object BackupFormat {
    const val MAGIC = "RHB1"
    const val SALT_SIZE = 16
    const val NONCE_SIZE = 12
    const val TAG_BITS = 128
    const val PBKDF2_ROUNDS = 210_000
    const val KEY_BITS = 256
}
