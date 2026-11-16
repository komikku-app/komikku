package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.Closeable
import java.io.InputStream

/**
 * KMK: desktop counterpart of the Android (libarchive) `ZipWriter`, same public API, on zip4j.
 * Entries are stored uncompressed, like on Android; with [encrypt] they are encrypted with the CBZ
 * password using the algorithm chosen in the security settings (AES-256, AES-128 or ZipCrypto).
 */
class ZipWriter(
    val context: Context,
    file: UniFile,
    encrypt: Boolean = false,
) : Closeable {
    private val encryption: Pair<EncryptionMethod, AesKeyStrength?>? =
        if (encrypt) encryptionFor(CbzCrypto.getPreferredEncryptionAlgo().decodeToString()) else null

    private val zip: ZipOutputStream = file.openOutputStream().let { output ->
        try {
            if (encryption != null) {
                val password = CbzCrypto.getDecryptedPasswordCbz()
                val chars = password.decodeToString().toCharArray()
                password.fill(0)
                ZipOutputStream(output, chars)
            } else {
                ZipOutputStream(output)
            }
        } catch (e: Exception) {
            output.close()
            throw e
        }
    }

    fun write(file: UniFile) {
        file.openInputStream().use {
            putEntry(requireNotNull(file.name) { "File has no name: ${file.uri}" }, file.length(), file.lastModified(), it)
        }
    }

    fun write(fileData: ByteArray, fileName: String) {
        putEntry(fileName, fileData.size.toLong(), System.currentTimeMillis(), fileData.inputStream())
    }

    private fun putEntry(name: String, size: Long, lastModified: Long, data: InputStream) {
        val parameters = ZipParameters().apply {
            fileNameInZip = name
            compressionMethod = CompressionMethod.STORE
            entrySize = size
            if (lastModified > 0) lastModifiedFileTime = lastModified
            encryption?.let { (method, strength) ->
                isEncryptFiles = true
                this.encryptionMethod = method
                strength?.let { aesKeyStrength = it }
            }
        }
        zip.putNextEntry(parameters)
        data.copyTo(zip, BUFFER_SIZE)
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }

    companion object {
        private const val BUFFER_SIZE = 8192

        /** Maps libarchive's zip encryption option (`CbzCrypto.getPreferredEncryptionAlgo`) to zip4j. */
        private fun encryptionFor(option: String): Pair<EncryptionMethod, AesKeyStrength?> =
            when (option.substringAfter("encryption=")) {
                "aes128" -> EncryptionMethod.AES to AesKeyStrength.KEY_STRENGTH_128
                "zipcrypt" -> EncryptionMethod.ZIP_STANDARD to null
                else -> EncryptionMethod.AES to AesKeyStrength.KEY_STRENGTH_256
            }
    }
}
