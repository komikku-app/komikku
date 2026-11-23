package app.komikku.sourcejs

import app.komikku.sourcejs.engine.Sha256

/** The SHA-256 of [text]'s UTF-8 bytes in lower-case hex, e.g. to check a downloaded extension text (I4). */
fun sha256Hex(text: String): String = Sha256.digest(text.encodeToByteArray()).toHexString()
