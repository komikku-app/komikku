package app.komikku.sourcejs.engine

import app.komikku.sourcejs.sha256Hex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Compiled scripts kept between calls, so a fresh context loads bytecode instead of parsing the prelude and the
 * extension again: the prelude once, extensions by [keyOf] their file name and text (least recently used dropped
 * beyond [capacity]; the file name is part of the bytecode, in its stack traces). Bytecode is only valid for the engine implementation that produced it, so one cache serves one
 * [JsEngineFactory] ([shared] is the one for [QuickJsEngine]).
 */
internal class BytecodeCache(private val capacity: Int = 32) {
    private val mutex = Mutex()
    private var prelude: ByteArray? = null
    private val extensions = LinkedHashMap<String, ByteArray>()

    suspend fun prelude(): ByteArray? = mutex.withLock { prelude }

    suspend fun putPrelude(bytecode: ByteArray) = mutex.withLock { prelude = bytecode }

    suspend fun extension(key: String): ByteArray? = mutex.withLock {
        // Re-insert to mark it as most recently used.
        extensions.remove(key)?.also { extensions[key] = it }
    }

    suspend fun putExtension(key: String, bytecode: ByteArray) = mutex.withLock {
        extensions.remove(key)
        extensions[key] = bytecode
        while (extensions.size > capacity) extensions.remove(extensions.keys.first())
    }

    /** Forgets everything, e.g. after bytecode failed to load. */
    suspend fun clear() = mutex.withLock {
        prelude = null
        extensions.clear()
    }

    companion object {
        /** Process-wide cache for [QuickJsEngine]. */
        val shared = BytecodeCache()

        /** The cache key of the extension text [code] compiled as [fileName]: a SHA-256 in hex. */
        fun keyOf(fileName: String, code: String): String = sha256Hex(fileName + "\u0000" + code)
    }
}

/** SHA-256 (FIPS 180-4) in pure Kotlin for every platform (cache keys, [sha256Hex]). */
internal object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun digest(message: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667,
            -0x4498517b,
            0x3c6ef372,
            -0x5ab00ac6,
            0x510e527f,
            -0x64fa9774,
            0x1f83d9ab,
            0x5be0cd19,
        )
        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 8) / 64 + 1) * 64
        val padded = message.copyOf(paddedLength)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[paddedLength - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in 0 until paddedLength step 64) {
            for (i in 0 until 16) {
                val o = chunk + 4 * i
                w[i] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or
                    ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }
            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[4 * i + j] = (h[i] ushr (24 - 8 * j)).toByte()
        return out
    }
}
