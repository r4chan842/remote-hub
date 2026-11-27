package app.remotehub.tools

import app.remotehub.data.createSecretBox
import java.io.File

/**
 * Seals a value with a repository public key using exactly the crypto implementation
 * that ships in the app, so the CLI and the client cannot drift apart.
 *
 * Usage: sealSecret <publicKeyBase64> [valueFile]
 * The value is read from the file, or from standard input when no file is given.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: sealSecret <publicKeyBase64> [valueFile]" }
    val publicKey = args[0]
    val value = if (args.size > 1) File(args[1]).readText() else System.`in`.readBytes().decodeToString()
    val sealed = createSecretBox().seal(value.trimEnd('\n'), publicKey)
    println(sealed)
}
