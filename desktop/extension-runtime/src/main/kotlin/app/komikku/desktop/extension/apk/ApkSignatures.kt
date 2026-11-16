package app.komikku.desktop.extension.apk

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.jar.JarFile

/** Outcome of reading and verifying an APK's signatures. */
sealed interface ApkSignatureResult {

    /**
     * Verified signatures. [certificates] are the DER encodings of the signers' certificates, in the
     * order the platform reports them (`PackageInfo.signingInfo`), [sha256] their lowercase hex
     * SHA-256 digests (the "signature hash" Komikku uses for trust decisions).
     */
    data class Signed(val scheme: Scheme, val certificates: List<ByteArray>) : ApkSignatureResult {
        val sha256: List<String> = certificates.map { sha256Hex(it) }
    }

    /** No signature at all. */
    data object Unsigned : ApkSignatureResult

    /** Signed, but the signature or the signed contents do not verify (tampered or unsupported). */
    data class Invalid(val reason: String) : ApkSignatureResult

    enum class Scheme { V1, V2, V3, V31 }
}

/**
 * Reads the signing certificates of an APK and verifies them, so that the certificate hashes can be
 * trusted like the ones Android's package manager reports.
 *
 * Order of preference (as on an API 34 device): APK Signature Scheme v3.1, v3, v2 (the APK signing
 * block before the ZIP central directory), then v1 (JAR signing, verified by the JDK's [JarFile]).
 * For v2 / v3 the signer's signature over its signed data and the chunked content digests of the
 * whole APK are verified. Not supported: v3 proof-of-rotation lineages (only the current signer is
 * reported, never unverified past signers), verity-only signature algorithms, source stamps, ZIP64.
 *
 * Written from the published APK Signature Scheme v2 / v3 format description.
 */
object ApkSignatures {

    private const val BLOCK_ID_V2 = 0x7109871a
    private const val BLOCK_ID_V3 = 0xf05368c0.toInt()
    private const val BLOCK_ID_V31 = 0x1b93ad61

    /** SDK level the v3 signer selection pretends to run on (the compat `Build.VERSION.SDK_INT`). */
    private const val PLATFORM_SDK = 34

    private const val EOCD_SIGNATURE = 0x06054b50
    private const val EOCD_MIN_SIZE = 22
    private const val SIGNING_BLOCK_MAGIC = "APK Sig Block 42"
    private const val CHUNK_SIZE = 1024 * 1024
    private const val MAX_APK_SIZE = 512L * 1024 * 1024

    private enum class Algorithm(
        val id: Int,
        val jcaName: String,
        val digest: String,
        val pss: PSSParameterSpec? = null,
    ) {
        RSA_PSS_SHA512(0x0102, "RSASSA-PSS", "SHA-512", PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1)),
        RSA_PKCS1_SHA512(0x0104, "SHA512withRSA", "SHA-512"),
        ECDSA_SHA512(0x0202, "SHA512withECDSA", "SHA-512"),
        RSA_PSS_SHA256(0x0101, "RSASSA-PSS", "SHA-256", PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)),
        RSA_PKCS1_SHA256(0x0103, "SHA256withRSA", "SHA-256"),
        ECDSA_SHA256(0x0201, "SHA256withECDSA", "SHA-256"),
        DSA_SHA256(0x0301, "SHA256withDSA", "SHA-256"),
        ;

        companion object {
            fun byId(id: Int): Algorithm? = entries.firstOrNull { it.id == id }
        }
    }

    private class InvalidSignatureException(message: String) : Exception(message)

    fun read(apk: File): ApkSignatureResult {
        if (apk.length() > MAX_APK_SIZE) return ApkSignatureResult.Invalid("APK too large")
        return read(apk.readBytes(), apk)
    }

    /** [file] is used for v1 verification only (the JDK verifies JAR signatures from a file). */
    fun read(bytes: ByteArray, file: File?): ApkSignatureResult {
        return try {
            val zip = ZipSections.find(bytes) ?: return ApkSignatureResult.Invalid("Not a ZIP file")
            val block = findSigningBlock(bytes, zip)
            val schemes = listOf(
                BLOCK_ID_V31 to ApkSignatureResult.Scheme.V31,
                BLOCK_ID_V3 to ApkSignatureResult.Scheme.V3,
                BLOCK_ID_V2 to ApkSignatureResult.Scheme.V2,
            )
            for ((id, scheme) in schemes) {
                val value = block?.pairs?.get(id) ?: continue
                val certificates = verifySchemeBlock(bytes, zip, block.start, value, isV3 = scheme != ApkSignatureResult.Scheme.V2)
                    ?: continue // v3 / v3.1 without a signer for this platform: fall back to the next scheme
                return ApkSignatureResult.Signed(scheme, certificates)
            }
            if (file != null) readV1(file) else ApkSignatureResult.Unsigned
        } catch (e: InvalidSignatureException) {
            ApkSignatureResult.Invalid(e.message ?: "Invalid signature")
        } catch (e: Exception) {
            ApkSignatureResult.Invalid("${e::class.simpleName}: ${e.message}")
        }
    }

    // ---- ZIP layout ------------------------------------------------------------------------------

    private class ZipSections(val centralDirectoryOffset: Int, val eocdOffset: Int) {
        companion object {
            fun find(bytes: ByteArray): ZipSections? {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val lowest = (bytes.size - EOCD_MIN_SIZE - 0xffff).coerceAtLeast(0)
                var at = bytes.size - EOCD_MIN_SIZE
                while (at >= lowest) {
                    if (buffer.getInt(at) == EOCD_SIGNATURE) {
                        val commentLength = buffer.getShort(at + 20).toInt() and 0xffff
                        if (at + EOCD_MIN_SIZE + commentLength == bytes.size) {
                            val cdSize = buffer.getInt(at + 12).toLong() and 0xffffffffL
                            val cdOffset = buffer.getInt(at + 16).toLong() and 0xffffffffL
                            if (cdOffset + cdSize != at.toLong()) return null
                            return ZipSections(cdOffset.toInt(), at)
                        }
                    }
                    at--
                }
                return null
            }
        }
    }

    private class SigningBlock(val start: Int, val pairs: Map<Int, ByteBuffer>)

    private fun findSigningBlock(bytes: ByteArray, zip: ZipSections): SigningBlock? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val cd = zip.centralDirectoryOffset
        if (cd < 32) return null
        val magic = String(bytes, cd - 16, 16, Charsets.US_ASCII)
        if (magic != SIGNING_BLOCK_MAGIC) return null
        val sizeInFooter = buffer.getLong(cd - 24)
        if (sizeInFooter < 24 || sizeInFooter > cd - 8) throw InvalidSignatureException("Bad APK signing block size")
        val start = (cd - sizeInFooter - 8).toInt()
        if (buffer.getLong(start) != sizeInFooter) throw InvalidSignatureException("APK signing block sizes differ")

        val pairs = mutableMapOf<Int, ByteBuffer>()
        var at = start + 8
        val end = cd - 24
        while (at < end) {
            if (end - at < 12) throw InvalidSignatureException("Truncated APK signing block")
            val length = buffer.getLong(at)
            if (length < 4 || length > end - at - 8) throw InvalidSignatureException("Bad APK signing block entry")
            val id = buffer.getInt(at + 8)
            val value = ByteBuffer.wrap(bytes, at + 12, (length - 4).toInt()).slice().order(ByteOrder.LITTLE_ENDIAN)
            pairs.putIfAbsent(id, value)
            at += 8 + length.toInt()
        }
        return SigningBlock(start, pairs)
    }

    // ---- v2 / v3 ---------------------------------------------------------------------------------

    /**
     * Verifies every signer of a v2 / v3 / v3.1 block and returns their certificates, or `null` when
     * the (v3) block has no signer for [PLATFORM_SDK].
     */
    private fun verifySchemeBlock(
        apk: ByteArray,
        zip: ZipSections,
        signingBlockStart: Int,
        value: ByteBuffer,
        isV3: Boolean,
    ): List<ByteArray>? {
        val signers = lengthPrefixed(value)
        val certificates = mutableListOf<ByteArray>()
        val contentDigests = mutableMapOf<String, ByteArray>()
        var signerCount = 0
        while (signers.hasRemaining()) {
            signerCount++
            val signer = lengthPrefixed(signers)
            val signedDataBuffer = lengthPrefixed(signer)
            var minSdk = 0
            var maxSdk = Int.MAX_VALUE
            if (isV3) {
                minSdk = signer.getInt()
                maxSdk = signer.getInt()
            }
            val signatures = lengthPrefixed(signer)
            val publicKeyBytes = bytes(lengthPrefixed(signer))
            if (isV3 && (PLATFORM_SDK < minSdk || PLATFORM_SDK > maxSdk)) continue

            // Strongest supported signature over the signed data
            val offered = mutableMapOf<Algorithm, ByteArray>()
            val offeredIds = mutableListOf<Int>()
            while (signatures.hasRemaining()) {
                val entry = lengthPrefixed(signatures)
                val id = entry.getInt()
                offeredIds += id
                Algorithm.byId(id)?.let { offered[it] = bytes(lengthPrefixed(entry)) }
            }
            val algorithm = Algorithm.entries.firstOrNull { it in offered }
                ?: throw InvalidSignatureException("No supported signature algorithm (${offeredIds.joinToString { "0x" + it.toString(16) }})")

            val signedData = bytes(signedDataBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
            val digests = lengthPrefixed(signedDataBuffer)
            val certs = lengthPrefixed(signedDataBuffer)
            if (!certs.hasRemaining()) throw InvalidSignatureException("Signer without certificate")
            val firstCertificate = bytes(lengthPrefixed(certs))
            val certificate = parseCertificate(firstCertificate)
            if (!certificate.publicKey.encoded.contentEquals(publicKeyBytes)) {
                throw InvalidSignatureException("Signer public key does not match its certificate")
            }
            if (!verify(algorithm, certificate.publicKey, signedData, offered.getValue(algorithm))) {
                throw InvalidSignatureException("Signature over signed data does not verify")
            }
            if (isV3) {
                val signedMinSdk = signedDataBuffer.getInt()
                val signedMaxSdk = signedDataBuffer.getInt()
                if (signedMinSdk != minSdk || signedMaxSdk != maxSdk) {
                    throw InvalidSignatureException("v3 signer SDK range differs from its signed data")
                }
            }

            val digestIds = mutableListOf<Int>()
            var expectedDigest: ByteArray? = null
            while (digests.hasRemaining()) {
                val entry = lengthPrefixed(digests)
                val id = entry.getInt()
                digestIds += id
                if (id == algorithm.id) expectedDigest = bytes(lengthPrefixed(entry))
            }
            if (digestIds.sorted() != offeredIds.sorted()) {
                throw InvalidSignatureException("Signature and digest algorithm lists differ")
            }
            val actual = contentDigests.getOrPut(algorithm.digest) {
                contentDigest(apk, zip, signingBlockStart, algorithm.digest)
            }
            if (expectedDigest == null || !MessageDigest.isEqual(expectedDigest, actual)) {
                throw InvalidSignatureException("APK contents do not match the signed digest")
            }
            certificates += firstCertificate
        }
        if (signerCount == 0) throw InvalidSignatureException("Signing block without signers")
        return certificates.ifEmpty { null }
    }

    private fun verify(algorithm: Algorithm, key: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return Signature.getInstance(algorithm.jcaName).run {
            initVerify(key)
            algorithm.pss?.let(::setParameter)
            update(data)
            verify(signature)
        }
    }

    /**
     * Chunked digest over the ZIP entries section, the central directory and the end of central
     * directory record whose central directory offset is replaced by the signing block offset.
     */
    private fun contentDigest(apk: ByteArray, zip: ZipSections, signingBlockStart: Int, algorithm: String): ByteArray {
        val eocd = apk.copyOfRange(zip.eocdOffset, apk.size)
        ByteBuffer.wrap(eocd).order(ByteOrder.LITTLE_ENDIAN).putInt(16, signingBlockStart)
        val sections = listOf(
            Triple(apk, 0, signingBlockStart),
            Triple(apk, zip.centralDirectoryOffset, zip.eocdOffset),
            Triple(eocd, 0, eocd.size),
        )
        val chunkCount = sections.sumOf { (_, from, to) -> (to - from + CHUNK_SIZE - 1) / CHUNK_SIZE }
        val top = MessageDigest.getInstance(algorithm)
        top.update(0x5a)
        top.update(int32(chunkCount))
        val chunkDigest = MessageDigest.getInstance(algorithm)
        sections.forEach { (data, from, to) ->
            var at = from
            while (at < to) {
                val length = minOf(CHUNK_SIZE, to - at)
                chunkDigest.update(0xa5.toByte())
                chunkDigest.update(int32(length))
                chunkDigest.update(data, at, length)
                top.update(chunkDigest.digest())
                at += length
            }
        }
        return top.digest()
    }

    // ---- v1 --------------------------------------------------------------------------------------

    /**
     * JAR signature verification by the JDK: every entry outside `META-INF/` must be signed by the
     * same signers. Note: the JDK treats SHA-1-only JAR signatures as unsigned (`jdk.jar.disabledAlgorithms`).
     */
    private fun readV1(file: File): ApkSignatureResult {
        JarFile(file, true).use { jar ->
            var signers: List<ByteArray>? = null
            var sawEntry = false
            val buffer = ByteArray(16 * 1024)
            for (entry in jar.entries()) {
                if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue
                sawEntry = true
                jar.getInputStream(entry).use { input -> while (input.read(buffer) != -1) Unit }
                val entrySigners = entry.codeSigners
                    ?.map { it.signerCertPath.certificates.first().encoded }
                    ?: return ApkSignatureResult.Unsigned
                val known = signers
                if (known == null) {
                    signers = entrySigners
                } else if (known.size != entrySigners.size || known.indices.any { !known[it].contentEquals(entrySigners[it]) }) {
                    return ApkSignatureResult.Invalid("JAR entries signed by different signers")
                }
            }
            val result = signers
            return if (!sawEntry || result.isNullOrEmpty()) {
                ApkSignatureResult.Unsigned
            } else {
                ApkSignatureResult.Signed(ApkSignatureResult.Scheme.V1, result)
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private fun lengthPrefixed(buffer: ByteBuffer): ByteBuffer {
        if (buffer.remaining() < 4) throw InvalidSignatureException("Truncated length-prefixed field")
        val length = buffer.getInt()
        if (length < 0 || length > buffer.remaining()) throw InvalidSignatureException("Bad length-prefixed field")
        val slice = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        slice.limit(length)
        buffer.position(buffer.position() + length)
        return slice
    }

    private fun bytes(buffer: ByteBuffer): ByteArray = ByteArray(buffer.remaining()).also { buffer.get(it) }

    private fun int32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun parseCertificate(bytes: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
