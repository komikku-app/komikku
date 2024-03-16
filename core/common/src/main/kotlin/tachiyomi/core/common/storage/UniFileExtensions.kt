package tachiyomi.core.common.storage

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import logcat.LogPriority
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.LocalFileHeader
import net.lingala.zip4j.model.ZipParameters
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

val UniFile.displayablePath: String
    get() = filePath ?: uri.toString()

fun UniFile.openReadOnlyChannel(context: Context): FileChannel {
    return ParcelFileDescriptor.AutoCloseInputStream(context.contentResolver.openFileDescriptor(uri, "r")).channel
// SY -->
}

fun UniFile.isEncryptedZip(): Boolean {
    return try {
        val stream = ZipInputStream(this.openInputStream())
        stream.nextEntry
        stream.close()
        false
    } catch (zipException: ZipException) {
        if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
            true
        } else {
            throw zipException
        }
    }
}

fun UniFile.testCbzPassword(): Boolean {
    return try {
        val stream = ZipInputStream(this.openInputStream())
        stream.setPassword(CbzCrypto.getDecryptedPasswordCbz())
        stream.nextEntry
        stream.close()
        true
    } catch (zipException: ZipException) {
        if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
            false
        } else {
            throw zipException
        }
    }
}

fun UniFile.addStreamToZip(inputStream: InputStream, filename: String, password: CharArray? = null) {
    val zipOutputStream =
        if (password != null) {
            ZipOutputStream(this.openOutputStream(), password)
        } else {
            ZipOutputStream(this.openOutputStream())
        }

    val zipParameters = ZipParameters()
    zipParameters.fileNameInZip = filename

    if (password != null) CbzCrypto.setZipParametersEncrypted(zipParameters)
    zipOutputStream.putNextEntry(zipParameters)

    zipOutputStream.use { output ->
        inputStream.use { input ->
            input.copyTo(output)
        }
    }
}

/**
 * Unzips encrypted or unencrypted zip files using zip4j.
 * The caller is responsible to ensure, that the file this is called from is a zip archive
 */
fun UniFile.unzip(destination: File, onlyCopyImages: Boolean = false) {
    destination.mkdirs()
    if (!destination.isDirectory) return

    val zipInputStream = ZipInputStream(this.openInputStream())
    var fileHeader: LocalFileHeader?

    if (this.isEncryptedZip()) {
        zipInputStream.setPassword(CbzCrypto.getDecryptedPasswordCbz())
    }
    try {
        while (
            run {
                fileHeader = zipInputStream.nextEntry
                fileHeader != null
            }
        ) {
            val tmpFile = File("${destination.absolutePath}/${fileHeader!!.fileName}")

            if (onlyCopyImages) {
                if (!fileHeader!!.isDirectory && ImageUtil.isImage(fileHeader!!.fileName)) {
                    tmpFile.createNewFile()
                    tmpFile.outputStream().buffered().use { tmpOut ->
                        zipInputStream.buffered().copyTo(tmpOut)
                    }
                }
            } else {
                if (!fileHeader!!.isDirectory && ImageUtil.isImage(fileHeader!!.fileName)) {
                    tmpFile.createNewFile()
                    tmpFile
                        .outputStream()
                        .buffered()
                        .use { zipInputStream.buffered().copyTo(it) }
                }
            }
        }
        zipInputStream.close()
    } catch (zipException: ZipException) {
        if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
            logcat(LogPriority.WARN) {
                "Wrong CBZ archive password for: ${this.name} in: ${this.parentFile?.name}"
            }
        } else {
            throw zipException
        }
    }
}

fun UniFile.addFilesToZip(files: List<UniFile>, password: CharArray? = null) {
    val zipOutputStream =
        if (password != null) {
            ZipOutputStream(this.openOutputStream(), password)
        } else {
            ZipOutputStream(this.openOutputStream())
        }

    files.forEach {
        val zipParameters = ZipParameters()
        if (password != null) CbzCrypto.setZipParametersEncrypted(zipParameters)
        zipParameters.fileNameInZip = it.name

        zipOutputStream.putNextEntry(zipParameters)

        it.openInputStream().use { input ->
            input.copyTo(zipOutputStream)
        }
        zipOutputStream.closeEntry()
    }
    zipOutputStream.close()
}

fun UniFile.getZipInputStream(filename: String): InputStream? {
    val zipInputStream = ZipInputStream(this.openInputStream())
    var fileHeader: LocalFileHeader?

    if (this.isEncryptedZip()) zipInputStream.setPassword(CbzCrypto.getDecryptedPasswordCbz())

    try {
        while (
            run {
                fileHeader = zipInputStream.nextEntry
                fileHeader != null
            }
        ) {
            if (fileHeader?.fileName == filename) return zipInputStream
        }
    } catch (zipException: ZipException) {
        if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
            logcat(LogPriority.WARN) {
                "Wrong CBZ archive password for: ${this.name} in: ${this.parentFile?.name}"
            }
        } else {
            throw zipException
        }
    }
    return null
}

fun UniFile.getCoverStreamFromZip(): InputStream? {
    val zipInputStream = ZipInputStream(this.openInputStream())
    var fileHeader: LocalFileHeader?
    val fileHeaderList: MutableList<LocalFileHeader?> = mutableListOf()

    if (this.isEncryptedZip()) zipInputStream.setPassword(CbzCrypto.getDecryptedPasswordCbz())

    try {
        while (
            run {
                fileHeader = zipInputStream.nextEntry
                fileHeader != null
            }
        ) {
            fileHeaderList.add(fileHeader)
        }
        var coverHeader = fileHeaderList
            .mapNotNull { it }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .find { !it.isDirectory && ImageUtil.isImage(it.fileName) }

        val coverStream = coverHeader?.fileName?.let { this.getZipInputStream(it) }
        if (coverStream != null) {
            if (!ImageUtil.isImage(coverHeader?.fileName) { coverStream }) coverHeader = null
        }
        return coverHeader?.fileName?.let { getZipInputStream(it) }
    } catch (zipException: ZipException) {
        if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
            logcat(LogPriority.WARN) {
                "Wrong CBZ archive password for: ${this.name} in: ${this.parentFile?.name}"
            }
            return null
        } else {
            throw zipException
        }
    }
}
// SY <--
