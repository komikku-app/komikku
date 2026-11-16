package android.util

import java.io.ByteArrayOutputStream

/**
 * android-compat: `android.util.Base64`, implemented from its documented behaviour:
 *
 * - Encoding: standard or [URL_SAFE] alphabet, `=` padding unless [NO_PADDING], output wrapped
 *   every 76 characters with `\n` (`\r\n` with [CRLF]) and a final line terminator unless
 *   [NO_WRAP].
 * - Decoding: lenient; characters outside the alphabet (whitespace, line breaks, the other
 *   alphabet's `+/` or `-_`) are skipped, padding is optional, malformed padding or a dangling
 *   single character throw [IllegalArgumentException] ("bad base-64").
 */
object Base64 {

    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    private const val LINE_LENGTH = 76

    private val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toByteArray()
    private val WEB_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toByteArray()

    private const val SKIP = -1
    private const val PAD = -2
    private val DECODE_STANDARD = decodeTable(STANDARD)
    private val DECODE_WEB_SAFE = decodeTable(WEB_SAFE)

    private fun decodeTable(alphabet: ByteArray): IntArray = IntArray(256) { SKIP }.apply {
        alphabet.forEachIndexed { index, char -> this[char.toInt()] = index }
        this['='.code] = PAD
    }

    // region encode

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = String(encode(input, flags), Charsets.US_ASCII)

    @JvmStatic
    fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String =
        String(encode(input, offset, len, flags), Charsets.US_ASCII)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = encode(input, 0, input.size, flags)

    @JvmStatic
    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        require(offset >= 0 && len >= 0 && offset + len <= input.size) { "bad offset/length" }
        val alphabet = if (flags and URL_SAFE != 0) WEB_SAFE else STANDARD
        val padding = flags and NO_PADDING == 0
        val wrap = flags and NO_WRAP == 0
        val newline = if (flags and CRLF != 0) byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()) else byteArrayOf('\n'.code.toByte())

        val out = ByteArrayOutputStream(len * 4 / 3 + 8)
        var lineLength = 0
        fun emit(value: Int) {
            out.write(alphabet[value and 0x3f].toInt())
            lineLength++
        }
        fun pad() {
            out.write('='.code)
            lineLength++
        }

        var i = offset
        val end = offset + len
        while (i + 3 <= end) {
            val chunk = (input[i].toInt() and 0xff shl 16) or (input[i + 1].toInt() and 0xff shl 8) or (input[i + 2].toInt() and 0xff)
            emit(chunk shr 18)
            emit(chunk shr 12)
            emit(chunk shr 6)
            emit(chunk)
            i += 3
            if (wrap && lineLength == LINE_LENGTH) {
                out.write(newline)
                lineLength = 0
            }
        }
        when (end - i) {
            1 -> {
                val chunk = input[i].toInt() and 0xff shl 16
                emit(chunk shr 18)
                emit(chunk shr 12)
                if (padding) {
                    pad()
                    pad()
                }
            }
            2 -> {
                val chunk = (input[i].toInt() and 0xff shl 16) or (input[i + 1].toInt() and 0xff shl 8)
                emit(chunk shr 18)
                emit(chunk shr 12)
                emit(chunk shr 6)
                if (padding) pad()
            }
        }
        // Every non-empty output ends with a line terminator when wrapping.
        if (wrap && lineLength > 0) out.write(newline)
        return out.toByteArray()
    }

    // endregion

    // region decode

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = decode(str.toByteArray(Charsets.ISO_8859_1), flags)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray = decode(input, 0, input.size, flags)

    @JvmStatic
    fun decode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        require(offset >= 0 && len >= 0 && offset + len <= input.size) { "bad offset/length" }
        val table = if (flags and URL_SAFE != 0) DECODE_WEB_SAFE else DECODE_STANDARD
        val out = ByteArrayOutputStream(len * 3 / 4)

        // State: number of data characters in the current quantum (0-3), 4 = one '=' seen after two
        // data characters (one more required), 5 = padding complete (only skippable input allowed).
        var state = 0
        var value = 0
        for (index in offset until offset + len) {
            val d = table[input[index].toInt() and 0xff]
            when (state) {
                0, 1 -> when {
                    d >= 0 -> {
                        value = (value shl 6) or d
                        state++
                    }
                    d != SKIP -> bad()
                }
                2 -> when {
                    d >= 0 -> {
                        value = (value shl 6) or d
                        state = 3
                    }
                    d == PAD -> {
                        out.write(value shr 4)
                        state = 4
                    }
                }
                3 -> when {
                    d >= 0 -> {
                        value = (value shl 6) or d
                        out.write(value shr 16)
                        out.write(value shr 8)
                        out.write(value)
                        value = 0
                        state = 0
                    }
                    d == PAD -> {
                        out.write(value shr 10)
                        out.write(value shr 2)
                        state = 5
                    }
                }
                4 -> when (d) {
                    PAD -> state = 5
                    SKIP -> Unit
                    else -> bad()
                }
                5 -> if (d != SKIP) bad()
            }
        }
        when (state) {
            1, 4 -> bad()
            2 -> out.write(value shr 4)
            3 -> {
                out.write(value shr 10)
                out.write(value shr 2)
            }
        }
        return out.toByteArray()
    }

    private fun bad(): Nothing = throw IllegalArgumentException("bad base-64")

    // endregion
}
