package app.komikku.desktop.extension.testing

import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

/** A signing key with a self-signed certificate, generated for tests. */
class TestSigningKey(val keyPair: KeyPair, val certificate: X509Certificate) {

    /** Lowercase hex SHA-256 of the certificate: the "signature hash" Android reports. */
    val sha256: String = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    companion object {
        fun rsa(commonName: String = "Komikku test"): TestSigningKey = generate("RSA", 2048, "SHA256withRSA", commonName)

        fun ec(commonName: String = "Komikku test EC"): TestSigningKey = generate("EC", 256, "SHA256withECDSA", commonName)

        private fun generate(algorithm: String, size: Int, signatureAlgorithm: String, commonName: String): TestSigningKey {
            val keyPair = KeyPairGenerator.getInstance(algorithm).apply { initialize(size) }.generateKeyPair()
            val name = X500Name("CN=$commonName")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                Date(now - 86_400_000L),
                Date(now + 365L * 86_400_000L),
                name,
                keyPair.public,
            ).build(JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.private))
            return TestSigningKey(keyPair, JcaX509CertificateConverter().getCertificate(holder))
        }
    }
}

/** Signs APKs with apksig (Google's reference implementation), independently of the code under test. */
object TestApkSigner {

    data class Schemes(val v1: Boolean = false, val v2: Boolean = true, val v3: Boolean = false)

    fun sign(unsigned: File, signed: File, key: TestSigningKey, schemes: Schemes = Schemes()) {
        val config = ApkSigner.SignerConfig.Builder("test", key.keyPair.private, listOf(key.certificate)).build()
        ApkSigner.Builder(listOf(config))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(MIN_SDK)
            .setV1SigningEnabled(schemes.v1)
            .setV2SigningEnabled(schemes.v2)
            .setV3SigningEnabled(schemes.v3)
            .setV4SigningEnabled(false)
            .build()
            .sign()
    }

    const val MIN_SDK = 21
}
