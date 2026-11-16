package app.komikku.desktop.extension.apk

import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.TestApkSigner
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Fixture APKs are signed by apksig, so the verifier is checked against an independent signer. */
class ApkSignaturesTest {

    @TempDir
    lateinit var dir: File

    private val marker = "TAMPER-ME-PLEASE".toByteArray()

    private fun signed(key: TestSigningKey, schemes: TestApkSigner.Schemes): File {
        val apk = File(dir, "signed-${System.nanoTime()}.apk")
        FixtureApk(extraEntries = mapOf("assets/marker.txt" to marker)).writeSigned(apk, key, schemes)
        return apk
    }

    @Test
    fun `v2 signature with an RSA key`() {
        val key = TestSigningKey.rsa()
        val result = ApkSignatures.read(signed(key, TestApkSigner.Schemes(v2 = true)))

        result.shouldBeInstanceOf<ApkSignatureResult.Signed>()
        result.scheme shouldBe ApkSignatureResult.Scheme.V2
        result.sha256 shouldContainExactly listOf(key.sha256)
    }

    @Test
    fun `v2 signature with an EC key`() {
        val key = TestSigningKey.ec()
        val result = ApkSignatures.read(signed(key, TestApkSigner.Schemes(v2 = true)))

        result.shouldBeInstanceOf<ApkSignatureResult.Signed>()
        result.sha256 shouldContainExactly listOf(key.sha256)
    }

    @Test
    fun `v3 signature is preferred over v2 and v1`() {
        val key = TestSigningKey.rsa()
        val result = ApkSignatures.read(signed(key, TestApkSigner.Schemes(v1 = true, v2 = true, v3 = true)))

        result.shouldBeInstanceOf<ApkSignatureResult.Signed>()
        result.scheme shouldBe ApkSignatureResult.Scheme.V3
        result.sha256 shouldContainExactly listOf(key.sha256)
    }

    @Test
    fun `v1 only signature is verified by the JDK`() {
        val key = TestSigningKey.rsa()
        val result = ApkSignatures.read(signed(key, TestApkSigner.Schemes(v1 = true, v2 = false)))

        result.shouldBeInstanceOf<ApkSignatureResult.Signed>()
        result.scheme shouldBe ApkSignatureResult.Scheme.V1
        result.sha256 shouldContainExactly listOf(key.sha256)
    }

    @Test
    fun `unsigned APK`() {
        val apk = File(dir, "unsigned.apk")
        FixtureApk().writeUnsigned(apk)

        ApkSignatures.read(apk) shouldBe ApkSignatureResult.Unsigned
    }

    @Test
    fun `modified contents fail v2 verification`() {
        val apk = signed(TestSigningKey.rsa(), TestApkSigner.Schemes(v2 = true))
        tamper(apk)

        ApkSignatures.read(apk).shouldBeInstanceOf<ApkSignatureResult.Invalid>()
    }

    @Test
    fun `modified contents fail v1 verification`() {
        val apk = signed(TestSigningKey.rsa(), TestApkSigner.Schemes(v1 = true, v2 = false))
        tamper(apk)

        ApkSignatures.read(apk).shouldBeInstanceOf<ApkSignatureResult.Invalid>()
    }

    /** Flips one byte of the stored marker entry (CRC fixed up so the ZIP itself stays readable). */
    private fun tamper(apk: File) {
        val bytes = apk.readBytes()
        val at = indexOf(bytes, marker)
        check(at >= 0)
        bytes[at] = 'X'.code.toByte()
        val tampered = bytes.copyOfRange(at, at + marker.size)
        val oldCrc = java.util.zip.CRC32().apply { update(marker) }.value.toInt()
        val newCrc = java.util.zip.CRC32().apply { update(tampered) }.value.toInt()
        val oldCrcBytes = le(oldCrc)
        var search = indexOf(bytes, oldCrcBytes)
        while (search >= 0) {
            le(newCrc).copyInto(bytes, search)
            search = indexOf(bytes, oldCrcBytes)
        }
        apk.writeBytes(bytes)
    }

    private fun le(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
