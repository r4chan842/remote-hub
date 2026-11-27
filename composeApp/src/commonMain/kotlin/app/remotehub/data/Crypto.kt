package app.remotehub.data

interface SecretBox {
    fun seal(message: String, recipientPublicKeyBase64: String): String
}

expect fun createSecretBox(): SecretBox

expect fun randomBytes(size: Int): ByteArray

fun randomPassword(length: Int = 20): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    val symbols = "!@#%*-_=+"
    val pool = alphabet + symbols
    val bytes = randomBytes(length)
    return bytes.joinToString("") { pool[(it.toInt() and 0xFF) % pool.length].toString() }
}

/** Lowercase letters and digits only: safe inside a workflow input and a hostname. */
fun randomSlug(length: Int): String {
    val pool = "abcdefghijklmnopqrstuvwxyz0123456789"
    return randomBytes(length).joinToString("") { pool[(it.toInt() and 0xFF) % pool.length].toString() }
}

fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
