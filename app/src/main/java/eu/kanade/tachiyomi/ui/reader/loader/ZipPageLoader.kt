package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import net.lingala.zip4j.ZipFile as Zip4JFile

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: File) : PageLoader() {

    private val context: Application by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    // SY -->
    init {
        val zip = Zip4JFile(file)
        if (zip.isEncrypted) {
            if (!CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())) {
                this.recycle()
                throw IllegalStateException(context.getString(R.string.wrong_cbz_archive_password))
            }
            unzipEncrypted(zip)
        } else {
            unzip(file)
        }
    }
    private fun unzip(file: File) {
        // SY <--
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            FileInputStream(file).use { it.copyTo(byteArrayOutputStream) }

            ZipFile(SeekableInMemoryByteChannel(byteArrayOutputStream.toByteArray())).use { zip ->
                zip.entries.asSequence()
                    .filterNot { it.isDirectory }
                    .forEach { entry ->
                        File(tmpDir, entry.name.substringAfterLast("/"))
                            .also { it.createNewFile() }
                            .outputStream().use { pageOutputStream ->
                                zip.getInputStream(entry).copyTo(pageOutputStream)
                                pageOutputStream.flush()
                            }
                    }
            }
        }
    }

    // SY -->
    private fun unzipEncrypted(zip: Zip4JFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            zip.charset = StandardCharsets.ISO_8859_1
        }
        zip.setPassword(CbzCrypto.getDecryptedPasswordCbz())

        zip.fileHeaders.asSequence()
            .filterNot { it.isDirectory }
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
