package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.engine.Sha256
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** The pure Kotlin [CommonHostCrypto] gives the same bytes as the JDK providers. */
class CommonHostCryptoJdkTest {
    private val random = Random(42)

    @Test
    fun aesCbcMatchesTheJdk() {
        for (keySize in listOf(16, 24, 32)) {
            for (length in listOf(0, 1, 15, 16, 17, 100, 1024)) {
                val key = random.nextBytes(keySize)
                val iv = random.nextBytes(16)
                val plain = random.nextBytes(length)
                val jdk = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                    doFinal(plain)
                }
                val ours = CommonHostCrypto.aesCbc(true, key, iv, plain)
                assertContentEquals(jdk, ours, "encrypt key=$keySize len=$length")
                assertContentEquals(plain, CommonHostCrypto.aesCbc(false, key, iv, jdk), "decrypt key=$keySize len=$length")
            }
        }
    }

    @Test
    fun aesGcmMatchesTheJdk() {
        for (keySize in listOf(16, 32)) {
            for (ivSize in listOf(12, 16)) {
                for (length in listOf(0, 5, 16, 33)) {
                    val key = random.nextBytes(keySize)
                    val iv = random.nextBytes(ivSize)
                    val plain = random.nextBytes(length)
                    val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                        doFinal(plain)
                    }
                    assertContentEquals(plain, CommonHostCrypto.aesGcmDecrypt(key, iv, sealed), "key=$keySize iv=$ivSize len=$length")
                    val tampered = sealed.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
                    assertFailsWith<IllegalArgumentException> { CommonHostCrypto.aesGcmDecrypt(key, iv, tampered) }
                    assertFailsWith<AEADBadTagException> {
                        Cipher.getInstance("AES/GCM/NoPadding").run {
                            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                            doFinal(tampered)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun sha256MatchesTheJdk() {
        for (length in listOf(0, 1, 55, 56, 63, 64, 65, 1000, 100_000)) {
            val data = random.nextBytes(length)
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(data), Sha256.digest(data), "len=$length")
        }
    }

    @Test
    fun md5MatchesTheJdk() {
        for (length in listOf(0, 1, 55, 56, 63, 64, 65, 1000)) {
            val data = random.nextBytes(length)
            assertContentEquals(MessageDigest.getInstance("MD5").digest(data), CommonHostCrypto.md5(data), "len=$length")
        }
    }
}
