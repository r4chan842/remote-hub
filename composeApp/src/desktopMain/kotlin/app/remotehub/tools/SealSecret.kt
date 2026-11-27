package app.remotehub.tools

import app.remotehub.data.createSecretBox

/** Seals a value with a repository public key so a human can verify the crypto path. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: sealSecret <base64-public-key> <value>" }
    val sealed = createSecretBox().seal(args[1], args[0])
    println(sealed)
}
