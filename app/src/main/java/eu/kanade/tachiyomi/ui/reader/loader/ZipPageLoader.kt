package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import net.lingala.zip4j.ZipFile
import tachiyomi.core.util.system.ImageUtil
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: File) : PageLoader() {

    // SY -->
    private val context: Application by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    init {
        ZipFile(file).use { zip ->
            if (zip.isEncrypted) {
                if (!CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())) {
                    this.recycle()
                    throw IllegalStateException(context.getString(R.string.wrong_cbz_archive_password))
                }
                unzip(zip, CbzCrypto.getDecryptedPasswordCbz())
            } else {
                unzip(zip)
            }
        }
    }
    private fun unzip(zip: ZipFile, password: CharArray? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            zip.charset = StandardCharsets.ISO_8859_1
        }

        if (password != null) {
            zip.setPassword(password)
        }

        zip.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { zip.getInputStream(it) } }
            .forEach { entry ->
                zip.extractFile(entry, tmpDir.absolutePath)
            }
    }
    // SY <--

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return DirectoryPageLoader(tmpDir).getPages()
    }
}
