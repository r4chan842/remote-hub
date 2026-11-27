package app.remotehub.data

interface SecretBox {
    fun seal(message: String, recipientPublicKeyBase64: String): String
}

expect fun createSecretBox(): SecretBox

expect fun randomBytes(size: Int): ByteArray

fun randomPassword(length: Int = 20): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#%*-_=+"
    val pool = upper + lower + digits + symbols
    val size = if (length < 8) 8 else length
    val chars = MutableList(size) { pool[pickIndex(pool.length)] }
    // Windows refuses a local password that does not mix character classes, and a
    // purely random draw misses one class often enough to break a session. Seed one
    // of each, then shuffle so their positions stay unpredictable.
    chars[0] = upper[pickIndex(upper.length)]
    chars[1] = lower[pickIndex(lower.length)]
    chars[2] = digits[pickIndex(digits.length)]
    chars[3] = symbols[pickIndex(symbols.length)]
    for (i in size - 1 downTo 1) {
        val j = pickIndex(i + 1)
        val swap = chars[i]
        chars[i] = chars[j]
        chars[j] = swap
    }
    return chars.joinToString("")
}

/** Lowercase letters and digits only: safe inside a workflow input and a hostname. */
fun randomSlug(length: Int): String {
    val pool = "abcdefghijklmnopqrstuvwxyz0123456789"
    return CharArray(length) { pool[pickIndex(pool.length)] }.concatToString()
}

/** Uniform index in [0, bound) from secure bytes, without modulo bias. */
private fun pickIndex(bound: Int): Int {
    require(bound > 0)
    val limit = 256 - (256 % bound)
    while (true) {
        val value = randomBytes(1)[0].toInt() and 0xFF
        if (value < limit) return value % bound
    }
}

fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
