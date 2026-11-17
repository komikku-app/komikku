package app.komikku.sourcejs.bridge

import kotlin.io.encoding.Base64

/** The crypto globals of contract 4.4 on top of a [HostCrypto], with Mangayomi's fallbacks. */
internal class CryptoFunctions(private val crypto: HostCrypto) {

    /**
     * `cryptoHandler(text, iv, key, encrypt)`: AES-CBC / PKCS#7 with the UTF-8 bytes of [key] and [iv]. Encrypt:
     * UTF-8 text → base64; decrypt: base64 → UTF-8 text. Any error returns [text] unchanged.
     */
    fun cryptoHandler(text: String, iv: String, key: String, encrypt: Boolean): String = try {
        if (encrypt) {
            Base64.encode(crypto.aesCbc(true, key.encodeToByteArray(), iv.encodeToByteArray(), text.encodeToByteArray()))
        } else {
            crypto.aesCbc(false, key.encodeToByteArray(), iv.encodeToByteArray(), Base64.decode(text.trim()))
                .decodeToString(throwOnInvalidSequence = true)
        }
    } catch (e: Exception) {
        text
    }

    /** `encryptAESCryptoJS(plain, passphrase)`: CryptoJS / OpenSSL `Salted__` format, AES-256-CBC. Throws on error. */
    fun encryptCryptoJs(plainText: String, passphrase: String): String {
        val salt = crypto.randomBytes(8)
        val (key, iv) = evpBytesToKey(passphrase.trim().encodeToByteArray(), salt)
        val cipher = crypto.aesCbc(true, key, iv, plainText.trim().encodeToByteArray())
        return Base64.encode(SALTED + salt + cipher)
    }

    /** `decryptAESCryptoJS(b64, passphrase)`: inverse of [encryptCryptoJs]. Throws on error. */
    fun decryptCryptoJs(encrypted: String, passphrase: String): String {
        val bytes = Base64.decode(encrypted.trim())
        require(bytes.size > 16 && bytes.copyOf(8).contentEquals(SALTED)) { "Not a CryptoJS \"Salted__\" payload" }
        val salt = bytes.copyOfRange(8, 16)
        val (key, iv) = evpBytesToKey(passphrase.trim().encodeToByteArray(), salt)
        return crypto.aesCbc(false, key, iv, bytes.copyOfRange(16, bytes.size)).decodeToString()
    }

    /**
     * `decryptAESGCM(b64, keyHex, ivHex, tagHex = "")`: [tagHex], when given, is appended to the ciphertext.
     * Returns the input on failure.
     */
    fun decryptAesGcm(encrypted: String, keyHex: String, ivHex: String, tagHex: String): String = try {
        val data = Base64.decode(encrypted.trim()) + hexToBytes(tagHex)
        crypto.aesGcmDecrypt(hexToBytes(keyHex), hexToBytes(ivHex), data).decodeToString(throwOnInvalidSequence = true)
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

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            require(clean.length % 2 == 0) { "Odd hex length" }
            return ByteArray(clean.length / 2) { clean.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        }

        /**
         * `deobfuscateJsPassword(s)` (4.4): JSFuck-style digits. Each top-level `[...]` group becomes the number of
         * `!+[]` in it (1–9), `0` when it is just `+[]`, `-` otherwise; each top-level `(...)` group becomes `.`.
         */
        fun deobfuscateJsPassword(input: String): String = buildString {
            var i = 0
            while (i < input.length) {
                val open = input[i]
                val close = when (open) {
                    '[' -> ']'
                    '(' -> ')'
                    else -> null
                }
                if (close == null) {
                    i++
                    continue
                }
                var depth = 0
                var end = i
                while (end < input.length) {
                    if (input[end] == open) depth++
                    if (input[end] == close) {
                        depth--
                        if (depth == 0) break
                    }
                    end++
                }
                val group = input.substring(i + 1, minOf(end, input.length))
                if (open == '(') {
                    append('.')
                } else {
                    val count = Regex.escape("!+[]").toRegex().findAll(group).count()
                    append(
                        when {
                            count in 1..9 -> count.digitToChar()
                            group == "+[]" -> '0'
                            else -> '-'
                        },
                    )
                }
                i = end + 1
            }
        }
    }
}
