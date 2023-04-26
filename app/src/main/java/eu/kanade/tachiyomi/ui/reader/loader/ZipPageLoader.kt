package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import net.lingala.zip4j.ZipFile
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(
    file: File,
    // SY -->
    context: Context,
    // SY <--
) : PageLoader() {

    private val context: Application by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    // SY -->
    init {
        val zip = ZipFile(file)
        if (zip.isEncrypted) {
            if (!CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())) {
                this.recycle()
                throw Exception(context.getString(R.string.wrong_cbz_archive_password))
            }
            unzipEncrypted(zip)
        } else {
            unzip(file)
        }
    }
    private fun unzip(file: File) {
        // SY <--
        ZipInputStream(FileInputStream(file)).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    File(tmpDir, entry.name.substringAfterLast("/"))
                        .also { it.createNewFile() }
                        .outputStream().use { pageOutputStream ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pageOutputStream.write(zipInputStream.readNBytes(entry.size.toInt()))
                            } else {
                                val buffer = ByteArray(2048)
                                var len: Int
                                while (
                                    zipInputStream.read(buffer, 0, buffer.size)
                                        .also { len = it } >= 0
                                ) {
                                    pageOutputStream.write(buffer, 0, len)
                                }
                            }
                            pageOutputStream.flush()
                        }
                    zipInputStream.closeEntry()
                }
        }
    }

    // SY -->
    private fun unzipEncrypted(zip: ZipFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            zip.charset = StandardCharsets.ISO_8859_1
        }
        zip.setPassword(CbzCrypto.getDecryptedPasswordCbz())

        zip.fileHeaders.asSequence()
            .filterNot { !it.isDirectory }
            .forEach { entry ->
                zip.extractFile(entry, tmpDir.absolutePath)
            }
    }
    // SY <--

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return DirectoryPageLoader(tmpDir).getPages()
    }

    override fun recycle() {
        super.recycle()
        tmpDir.deleteRecursively()
    }
}
