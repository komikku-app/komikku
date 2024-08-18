package eu.kanade.tachiyomi.util.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.core.common.archive.ArchiveReader
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.injectLazy
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

// SY -->
/**
 * object used to En/Decrypt and Base64 en/decode
 * passwords before storing
 * them in Shared Preferences
 */
object CbzCrypto {
    const val DATABASE_NAME = "tachiyomiEncrypted.db"
    private const val DEFAULT_COVER_NAME = "cover.jpg"
    private val securityPreferences: SecurityPreferences by injectLazy()
    private val keyStore = KeyStore.getInstance(Keystore).apply {
        load(null)
    }

    private val encryptionCipherCbz
        get() = Cipher.getInstance(CryptoSettings).apply {
            init(
                Cipher.ENCRYPT_MODE,
                getKey(AliasCbz),
            )
        }

    private val encryptionCipherSql
        get() = Cipher.getInstance(CryptoSettings).apply {
            init(
                Cipher.ENCRYPT_MODE,
                getKey(AliasSql),
            )
        }

    private fun getDecryptCipher(iv: ByteArray, alias: String): Cipher {
        return Cipher.getInstance(CryptoSettings).apply {
            init(
                Cipher.DECRYPT_MODE,
                getKey(alias),
                IvParameterSpec(iv),
            )
        }
    }

    private fun getKey(alias: String): SecretKey {
        val loadedKey = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return loadedKey?.secretKey ?: generateKey(alias)
    }

    private fun generateKey(alias: String): SecretKey {
        return KeyGenerator.getInstance(Algorithm).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(KeySize)
                    .setBlockModes(BlockMode)
                    .setEncryptionPaddings(Padding)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(password: ByteArray, cipher: Cipher): String {
        val outputStream = ByteArrayOutputStream()
        outputStream.use { output ->
            output.write(cipher.iv)
            ByteArrayInputStream(password).use { input ->
                val buffer = ByteArray(BufferSize)
                while (input.available() > BufferSize) {
                    input.read(buffer)
                    output.write(cipher.update(buffer))
                }
                output.write(cipher.doFinal(input.readBytes()))
            }
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun decrypt(encryptedPassword: String, alias: String): ByteArray {
        val inputStream = Base64.decode(encryptedPassword, Base64.DEFAULT).inputStream()
        return inputStream.use { input ->
            val iv = ByteArray(IvSize)
            input.read(iv)
            val cipher = getDecryptCipher(iv, alias)
            ByteArrayOutputStreamPassword().use { output ->
                val buffer = ByteArray(BufferSize)
                while (inputStream.available() > BufferSize) {
                    inputStream.read(buffer)
                    output.write(cipher.update(buffer))
                }
                output.write(cipher.doFinal(inputStream.readBytes()))
                output.toByteArray().also {
                    output.clear()
                }
            }
        }
    }

    fun deleteKeyCbz() {
        keyStore.deleteEntry(AliasCbz)
        generateKey(AliasCbz)
    }

    fun encryptCbz(password: String): String {
        return encrypt(password.toByteArray(), encryptionCipherCbz)
    }

    fun getDecryptedPasswordCbz(): ByteArray {
        val encryptedPassword = securityPreferences.cbzPassword().get()
        if (encryptedPassword.isBlank()) error("This archive is encrypted please set a password")

        return decrypt(encryptedPassword, AliasCbz)
    }

    private fun generateAndEncryptSqlPw() {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val passwordArray = CharArray(SqlPasswordLength)
        for (i in 0..<SqlPasswordLength) {
            passwordArray[i] = charPool[SecureRandom().nextInt(charPool.size)]
        }
        val passwordBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(passwordArray))
        val passwordBytes = ByteArray(passwordBuffer.limit())
        passwordBuffer.get(passwordBytes)
        securityPreferences.sqlPassword().set(encrypt(passwordBytes, encryptionCipherSql))
            .also {
                passwordArray.fill('#')
                passwordBuffer.array().fill('#'.code.toByte())
                passwordBytes.fill('#'.code.toByte())
            }
    }

    fun getDecryptedPasswordSql(): ByteArray {
        if (securityPreferences.sqlPassword().get().isBlank()) generateAndEncryptSqlPw()
        return decrypt(securityPreferences.sqlPassword().get(), AliasSql)
    }

    fun isPasswordSet(): Boolean {
        return securityPreferences.cbzPassword().get().isNotEmpty()
    }

    fun isPasswordSetState(scope: CoroutineScope): StateFlow<Boolean> {
        return securityPreferences.cbzPassword().changes()
            .map { it.isNotEmpty() }
            .stateIn(scope, SharingStarted.Eagerly, false)
    }

    fun getPasswordProtectDlPref(): Boolean {
        return securityPreferences.passwordProtectDownloads().get()
    }

    fun createComicInfoPadding(): String? {
        return if (getPasswordProtectDlPref()) {
            val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            List(SecureRandom().nextInt(100) + 42) { charPool.random() }.joinToString("")
        } else {
            null
        }
    }

    fun getPreferredEncryptionAlgo(): ByteArray =
        when (securityPreferences.encryptionType().get()) {
            SecurityPreferences.EncryptionType.AES_256 -> "zip:encryption=aes256".toByteArray()
            SecurityPreferences.EncryptionType.AES_128 -> "zip:encryption=aes128".toByteArray()
            SecurityPreferences.EncryptionType.ZIP_STANDARD -> "zip:encryption=zipcrypt".toByteArray()
        }

    fun detectCoverImageArchive(stream: InputStream): Boolean {
        val bytes = ByteArray(128)
        if (stream.markSupported()) {
            stream.mark(bytes.size)
            stream.read(bytes, 0, bytes.size).also { stream.reset() }
        } else {
            stream.read(bytes, 0, bytes.size)
        }
        return String(bytes).contains(DEFAULT_COVER_NAME, ignoreCase = true)
    }

    fun ArchiveReader.getCoverStream(): BufferedInputStream? {
        this.getInputStream(DEFAULT_COVER_NAME)?.let { stream ->
            if (ImageUtil.isImage(DEFAULT_COVER_NAME) { stream }) {
                return this.getInputStream(DEFAULT_COVER_NAME)?.buffered()
            }
        }
        return null
    }
}

private const val BufferSize = 2048
private const val KeySize = 256
private const val IvSize = 16

private const val Algorithm = KeyProperties.KEY_ALGORITHM_AES
private const val BlockMode = KeyProperties.BLOCK_MODE_CBC
private const val Padding = KeyProperties.ENCRYPTION_PADDING_PKCS7
private const val CryptoSettings = "$Algorithm/$BlockMode/$Padding"

private const val Keystore = "AndroidKeyStore"
private const val AliasCbz = "cbzPw"
private const val AliasSql = "sqlPw"

private const val SqlPasswordLength = 32

private class ByteArrayOutputStreamPassword : ByteArrayOutputStream() {
    fun clear() {
        this.buf.fill('#'.code.toByte())
    }
}
// SY <--
