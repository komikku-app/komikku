package app.komikku.sourcejs.bridge

import kotlin.io.encoding.Base64

/**
 * The crypto globals of contract 4.4 on top of a [HostCrypto], matching Mangayomi's Dart helpers byte for byte:
 * Dart's `base64.decode` (both alphabets, padding required), the `encrypt` package's decryption to text
 * (`utf8.decode(allowMalformed: true)`), `package:convert`'s hex decoder and `CryptoAES`'s key derivation.
 */
internal class CryptoFunctions(private val crypto: HostCrypto) {

    /**
     * `cryptoHandler(text, iv, key, encrypt)`: AES-CBC / PKCS#7 with the UTF-8 bytes of [key] and [iv]. Encrypt:
     * UTF-8 text → base64; decrypt: base64 → text (malformed UTF-8 becomes U+FFFD). Any error returns [text]
     * unchanged.
     */
    fun cryptoHandler(text: String, iv: String, key: String, encrypt: Boolean): String = try {
        if (encrypt) {
            Base64.encode(crypto.aesCbc(true, key.encodeToByteArray(), iv.encodeToByteArray(), text.encodeToByteArray()))
        } else {
            crypto.aesCbc(false, key.encodeToByteArray(), iv.encodeToByteArray(), dartBase64Decode(text)).decodeToString()
        }
    } catch (e: Exception) {
        text
    }

    /**
     * `encryptAESCryptoJS(plain, passphrase)`: CryptoJS / OpenSSL `Salted__` format, AES-256-CBC, key and IV from
     * [evpBytesToKey] over the trimmed passphrase; the trimmed plain text is encrypted as UTF-8. Throws on error.
     */
    fun encryptCryptoJs(plainText: String, passphrase: String): String {
        // Like Mangayomi: salt bytes in 1..245.
        val salt = crypto.randomBytes(8).map { ((it.toInt() and 0xFF) % 245 + 1).toByte() }.toByteArray()
        val (key, iv) = evpBytesToKey(codeUnitBytes(passphrase.trim()), salt)
        val cipher = crypto.aesCbc(true, key, iv, plainText.trim().encodeToByteArray())
        return Base64.encode(SALTED + salt + cipher)
    }

    /**
     * `decryptAESCryptoJS(b64, passphrase)`: salt from bytes 8–16 and ciphertext from byte 16 of the decoded input
     * (the `Salted__` prefix is not checked, as in Mangayomi). Throws on error.
     */
    fun decryptCryptoJs(encrypted: String, passphrase: String): String {
        val bytes = dartBase64Decode(encrypted.trim())
        require(bytes.size >= 16) { "RangeError: input too short (${bytes.size} bytes)" }
        val (key, iv) = evpBytesToKey(codeUnitBytes(passphrase.trim()), bytes.copyOfRange(8, 16))
        return crypto.aesCbc(false, key, iv, bytes.copyOfRange(16, bytes.size)).decodeToString()
    }

    /**
     * `decryptAESGCM(b64, keyHex, ivHex, tagHex = "")`: [tagHex], when given, is appended to the ciphertext.
     * Returns the input on failure.
     */
    fun decryptAesGcm(encrypted: String, keyHex: String, ivHex: String, tagHex: String): String = try {
        val key = keyHex.hexToByteArray()
        val iv = ivHex.hexToByteArray()
        val data = dartBase64Decode(encrypted) + tagHex.hexToByteArray()
        crypto.aesGcmDecrypt(key, iv, data).decodeToString()
    } catch (e: Exception) {
        encrypted
    }

    /** OpenSSL `EVP_BytesToKey` with MD5, one iteration: 32-byte key + 16-byte IV. */
    private fun evpBytesToKey(password: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        var derived = ByteArray(0)
        var block = ByteArray(0)
        while (derived.size < 48) {
            block = crypto.md5(block + password + salt)
            derived += block
        }
        return derived.copyOf(32) to derived.copyOfRange(32, 48)
    }

    companion object {
        private val SALTED = "Salted__".encodeToByteArray()

        /** Mangayomi's `createUint8ListFromString`: the low byte of each UTF-16 code unit. */
        fun codeUnitBytes(text: String): ByteArray = ByteArray(text.length) { text[it].code.toByte() }

        /**
         * Dart's `base64.decode`: the standard and the URL-safe alphabet (mixed too), padding required (`=` or
         * `%3D`), no whitespace.
         */
        fun dartBase64Decode(text: String): ByteArray {
            val normalized = text.replace("%3D", "=").replace("%3d", "=").replace('-', '+').replace('_', '/')
            return Base64.Default.decode(normalized)
        }

        /**
         * `deobfuscateJsPassword(s)` (4.4), Mangayomi's `Deobfuscator`: each `[...]` group becomes the number of
         * `!+[]` in it (1–9), `0` when it has none and exactly one `+[]`, `-` otherwise; each `(...)` group
         * becomes `.` and swallows a `[...]` group that directly follows it. Unbalanced brackets, or a `(...)`
         * group at the very end, are errors (Mangayomi throws a `RangeError` or loops).
         */
        fun deobfuscateJsPassword(input: String): String = buildString {
            var index = 0
            while (index < input.length) {
                val char = input[index]
                if (char != '[' && char != '(') {
                    index++
                    continue
                }
                val closing = matchingBracket(index, input)
                require(closing >= 0) { "Unbalanced brackets at index $index" }
                if (char == '[') {
                    append(digit(input.substring(index, closing)))
                } else {
                    append('.')
                    require(closing + 1 < input.length) { "RangeError: index ${closing + 1} out of range" }
                    if (input[closing + 1] == '[') {
                        val skipped = matchingBracket(closing + 1, input)
                        require(skipped >= 0) { "Unbalanced brackets at index ${closing + 1}" }
                        index = skipped + 1
                        continue
                    }
                }
                index = closing + 1
            }
        }

        private fun matchingBracket(opening: Int, input: String): Int {
            val open = input[opening]
            val close = if (open == '[') ']' else ')'
            var depth = 0
            for (index in opening until input.length) {
                if (input[index] == open) depth++
                if (input[index] == close) depth--
                if (depth == 0) return index
                if (depth < 0) return -1
            }
            return -1
        }

        private fun digit(group: String): Char {
            val ones = Regex("!\\+\\[]").findAll(group).count()
            return when {
                ones == 0 -> if (Regex("\\+\\[]").findAll(group).count() == 1) '0' else '-'
                ones <= 9 -> ones.digitToChar()
                else -> '-'
            }
        }
    }
}
