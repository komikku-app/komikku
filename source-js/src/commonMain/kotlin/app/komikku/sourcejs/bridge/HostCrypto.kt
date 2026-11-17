package app.komikku.sourcejs.bridge

import kotlin.random.Random

/**
 * Cryptographic primitives behind the crypto helpers of contract 4.4 (`cryptoHandler`, `encryptAESCryptoJS`,
 * `decryptAESCryptoJS`, `decryptAESGCM`). Implementations throw on invalid input (bad key size, bad padding,
 * failed authentication); the helpers apply Mangayomi's fallbacks.
 */
interface HostCrypto {
    /** AES-CBC with PKCS#7 padding; [key] is 16, 24 or 32 bytes, [iv] 16 bytes. */
    fun aesCbc(encrypt: Boolean, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    /** AES-GCM decryption of [ciphertextAndTag] (16-byte tag appended); throws when authentication fails. */
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray): ByteArray

    fun md5(data: ByteArray): ByteArray

    fun randomBytes(size: Int): ByteArray
}

/**
 * Pure Kotlin [HostCrypto] for every platform (no provider differences between JVM and iOS). Only used for the
 * few sources that decrypt site data, so speed does not matter; checked against the JDK in the desktop tests.
 */
object CommonHostCrypto : HostCrypto {

    override fun aesCbc(encrypt: Boolean, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        require(iv.size == BLOCK) { "IV must be 16 bytes, was ${iv.size}" }
        val aes = Aes(key)
        return if (encrypt) {
            val padLength = BLOCK - data.size % BLOCK
            val padded = data + ByteArray(padLength) { padLength.toByte() }
            val out = ByteArray(padded.size)
            var previous = iv
            for (offset in padded.indices step BLOCK) {
                val block = ByteArray(BLOCK) { (padded[offset + it].toInt() xor previous[it].toInt()).toByte() }
                aes.encryptBlock(block)
                block.copyInto(out, offset)
                previous = block
            }
            out
        } else {
            require(data.isNotEmpty() && data.size % BLOCK == 0) { "Ciphertext length must be a multiple of 16" }
            val out = ByteArray(data.size)
            var previous = iv
            for (offset in data.indices step BLOCK) {
                val cipherBlock = data.copyOfRange(offset, offset + BLOCK)
                val block = cipherBlock.copyOf()
                aes.decryptBlock(block)
                for (i in 0 until BLOCK) out[offset + i] = (block[i].toInt() xor previous[i].toInt()).toByte()
                previous = cipherBlock
            }
            val padLength = out.last().toInt() and 0xFF
            require(padLength in 1..BLOCK && (out.size - padLength until out.size).all { out[it].toInt() and 0xFF == padLength }) {
                "Bad padding"
            }
            out.copyOf(out.size - padLength)
        }
    }

    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray): ByteArray {
        require(ciphertextAndTag.size >= BLOCK) { "Ciphertext shorter than the tag" }
        val aes = Aes(key)
        val ciphertext = ciphertextAndTag.copyOf(ciphertextAndTag.size - BLOCK)
        val tag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - BLOCK, ciphertextAndTag.size)
        val hashKey = ByteArray(BLOCK).also(aes::encryptBlock)
        val j0 = if (iv.size == 12) {
            iv + byteArrayOf(0, 0, 0, 1)
        } else {
            ghash(hashKey, ByteArray(0), iv)
        }
        val expectedTag = ghash(hashKey, ByteArray(0), ciphertext).also { s ->
            val mask = j0.copyOf().also(aes::encryptBlock)
            for (i in 0 until BLOCK) s[i] = (s[i].toInt() xor mask[i].toInt()).toByte()
        }
        var diff = 0
        for (i in 0 until BLOCK) diff = diff or (expectedTag[i].toInt() xor tag[i].toInt())
        require(diff == 0) { "Authentication failed" }
        val out = ByteArray(ciphertext.size)
        val counter = j0.copyOf()
        for (offset in ciphertext.indices step BLOCK) {
            incrementCounter(counter)
            val stream = counter.copyOf().also(aes::encryptBlock)
            for (i in 0 until minOf(BLOCK, ciphertext.size - offset)) {
                out[offset + i] = (ciphertext[offset + i].toInt() xor stream[i].toInt()).toByte()
            }
        }
        return out
    }

    override fun md5(data: ByteArray): ByteArray = Md5.digest(data)

    override fun randomBytes(size: Int): ByteArray = Random.Default.nextBytes(size)

    private const val BLOCK = 16

    private fun incrementCounter(counter: ByteArray) {
        for (i in 15 downTo 12) {
            counter[i] = (counter[i] + 1).toByte()
            if (counter[i].toInt() != 0) return
        }
    }

    /** GHASH over [aad] and [data] with the lengths block (NIST SP 800-38D). */
    private fun ghash(hashKey: ByteArray, aad: ByteArray, data: ByteArray): ByteArray {
        val y = ByteArray(BLOCK)
        fun absorb(bytes: ByteArray) {
            for (offset in bytes.indices step BLOCK) {
                for (i in 0 until minOf(BLOCK, bytes.size - offset)) y[i] = (y[i].toInt() xor bytes[offset + i].toInt()).toByte()
                gfMultiply(y, hashKey)
            }
        }
        absorb(aad)
        absorb(data)
        val lengths = ByteArray(BLOCK)
        val aadBits = aad.size.toLong() * 8
        val dataBits = data.size.toLong() * 8
        for (i in 0 until 8) {
            lengths[7 - i] = (aadBits ushr (8 * i)).toByte()
            lengths[15 - i] = (dataBits ushr (8 * i)).toByte()
        }
        for (i in 0 until BLOCK) y[i] = (y[i].toInt() xor lengths[i].toInt()).toByte()
        gfMultiply(y, hashKey)
        return y
    }

    /** x = x * h in GF(2^128) with the GCM bit order. */
    private fun gfMultiply(x: ByteArray, h: ByteArray) {
        val z = ByteArray(BLOCK)
        val v = h.copyOf()
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) {
                for (j in 0 until BLOCK) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            }
            val lsb = v[15].toInt() and 1
            for (j in 15 downTo 1) v[j] = ((v[j].toInt() and 0xFF ushr 1) or ((v[j - 1].toInt() and 1) shl 7)).toByte()
            v[0] = (v[0].toInt() and 0xFF ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xE1).toByte()
        }
        z.copyInto(x)
    }
}

/** AES block cipher (FIPS-197), 128/192/256-bit keys. */
private class Aes(key: ByteArray) {
    private val rounds: Int
    private val roundKeys: IntArray

    init {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "AES key must be 16, 24 or 32 bytes, was ${key.size}" }
        val nk = key.size / 4
        rounds = nk + 6
        roundKeys = IntArray(4 * (rounds + 1))
        for (i in 0 until nk) {
            roundKeys[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or (key[4 * i + 3].toInt() and 0xFF)
        }
        var rcon = 1
        for (i in nk until roundKeys.size) {
            var t = roundKeys[i - 1]
            if (i % nk == 0) {
                t = subWord((t shl 8) or (t ushr 24)) xor (rcon shl 24)
                rcon = xtime(rcon)
            } else if (nk > 6 && i % nk == 4) {
                t = subWord(t)
            }
            roundKeys[i] = roundKeys[i - nk] xor t
        }
    }

    fun encryptBlock(block: ByteArray) {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, 0)
        for (round in 1 until rounds) {
            for (i in 0 until 16) s[i] = SBOX[s[i]]
            shiftRows(s)
            mixColumns(s)
            addRoundKey(s, round)
        }
        for (i in 0 until 16) s[i] = SBOX[s[i]]
        shiftRows(s)
        addRoundKey(s, rounds)
        for (i in 0 until 16) block[i] = s[i].toByte()
    }

    fun decryptBlock(block: ByteArray) {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, rounds)
        for (round in rounds - 1 downTo 1) {
            invShiftRows(s)
            for (i in 0 until 16) s[i] = INV_SBOX[s[i]]
            addRoundKey(s, round)
            invMixColumns(s)
        }
        invShiftRows(s)
        for (i in 0 until 16) s[i] = INV_SBOX[s[i]]
        addRoundKey(s, 0)
        for (i in 0 until 16) block[i] = s[i].toByte()
    }

    // State layout: s[4 * column + row], as the bytes come in.
    private fun addRoundKey(s: IntArray, round: Int) {
        for (c in 0 until 4) {
            val w = roundKeys[4 * round + c]
            s[4 * c] = s[4 * c] xor (w ushr 24 and 0xFF)
            s[4 * c + 1] = s[4 * c + 1] xor (w ushr 16 and 0xFF)
            s[4 * c + 2] = s[4 * c + 2] xor (w ushr 8 and 0xFF)
            s[4 * c + 3] = s[4 * c + 3] xor (w and 0xFF)
        }
    }

    private fun shiftRows(s: IntArray) {
        val t = s.copyOf()
        for (r in 1 until 4) for (c in 0 until 4) s[4 * c + r] = t[4 * ((c + r) % 4) + r]
    }

    private fun invShiftRows(s: IntArray) {
        val t = s.copyOf()
        for (r in 1 until 4) for (c in 0 until 4) s[4 * ((c + r) % 4) + r] = t[4 * c + r]
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[4 * c]
            val a1 = s[4 * c + 1]
            val a2 = s[4 * c + 2]
            val a3 = s[4 * c + 3]
            s[4 * c] = xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3
            s[4 * c + 1] = a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3
            s[4 * c + 2] = a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)
            s[4 * c + 3] = (xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)
        }
    }

    private fun invMixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[4 * c]
            val a1 = s[4 * c + 1]
            val a2 = s[4 * c + 2]
            val a3 = s[4 * c + 3]
            s[4 * c] = mul(a0, 14) xor mul(a1, 11) xor mul(a2, 13) xor mul(a3, 9)
            s[4 * c + 1] = mul(a0, 9) xor mul(a1, 14) xor mul(a2, 11) xor mul(a3, 13)
            s[4 * c + 2] = mul(a0, 13) xor mul(a1, 9) xor mul(a2, 14) xor mul(a3, 11)
            s[4 * c + 3] = mul(a0, 11) xor mul(a1, 13) xor mul(a2, 9) xor mul(a3, 14)
        }
    }

    private companion object {
        val SBOX = IntArray(256)
        val INV_SBOX = IntArray(256)

        init {
            // Build the S-box from the multiplicative inverse and the affine transform.
            var p = 1
            var q = 1
            do {
                p = p xor (p shl 1) xor (if (p and 0x80 != 0) 0x1B else 0)
                p = p and 0xFF
                q = q xor (q shl 1)
                q = q xor (q shl 2)
                q = q xor (q shl 4)
                q = q and 0xFF
                if (q and 0x80 != 0) q = q xor 0x09
                val x = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
                SBOX[p] = (x xor 0x63) and 0xFF
            } while (p != 1)
            SBOX[0] = 0x63
            for (i in 0 until 256) INV_SBOX[SBOX[i]] = i
        }

        fun rotl8(x: Int, shift: Int): Int = ((x shl shift) or (x ushr (8 - shift))) and 0xFF

        fun xtime(x: Int): Int = ((x shl 1) xor (if (x and 0x80 != 0) 0x1B else 0)) and 0xFF

        fun mul(a: Int, b: Int): Int {
            var result = 0
            var x = a
            var y = b
            while (y != 0) {
                if (y and 1 != 0) result = result xor x
                x = xtime(x)
                y = y ushr 1
            }
            return result
        }

        fun subWord(w: Int): Int = (SBOX[w ushr 24 and 0xFF] shl 24) or (SBOX[w ushr 16 and 0xFF] shl 16) or
            (SBOX[w ushr 8 and 0xFF] shl 8) or SBOX[w and 0xFF]
    }
}

/** MD5 (RFC 1321); only used for CryptoJS's `EVP_BytesToKey` key derivation. */
private object Md5 {
    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    private val K = IntArray(64) { i ->
        // floor(abs(sin(i + 1)) * 2^32)
        (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    fun digest(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 8) / 64 + 1) * 64
        val padded = message.copyOf(paddedLength)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val m = IntArray(16)
        for (chunk in 0 until paddedLength step 64) {
            for (i in 0 until 16) {
                val o = chunk + 4 * i
                m[i] = (padded[o].toInt() and 0xFF) or ((padded[o + 1].toInt() and 0xFF) shl 8) or
                    ((padded[o + 2].toInt() and 0xFF) shl 16) or ((padded[o + 3].toInt() and 0xFF) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i / 16) {
                    0 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    1 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) % 16
                    }
                    2 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val temp = d
                d = c
                c = b
                b += (a + f + K[i] + m[g]).rotateLeft(S[i])
                a = temp
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }
        val out = ByteArray(16)
        for ((index, word) in intArrayOf(a0, b0, c0, d0).withIndex()) {
            for (i in 0 until 4) out[4 * index + i] = (word ushr (8 * i)).toByte()
        }
        return out
    }
}
