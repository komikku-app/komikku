package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.os.Build
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import net.lingala.zip4j.ZipFile as Zip4jFile

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: File) : PageLoader() {

    // SY -->
    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
    }
    private val zip4j: Zip4jFile = Zip4jFile(file)
    private val zip: ZipFile? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!zip4j.isEncrypted) ZipFile(file, StandardCharsets.ISO_8859_1) else null
        } else {
            if (!zip4j.isEncrypted) ZipFile(file) else null
        }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            zip4j.charset = StandardCharsets.ISO_8859_1
        }

        Zip4jFile(file).use { zip ->
            if (zip.isEncrypted) {
                if (!CbzCrypto.checkCbzPassword(zip, CbzCrypto.getDecryptedPasswordCbz())) {
                    this.recycle()
                    throw IllegalStateException(context.stringResource(SYMR.strings.wrong_cbz_archive_password))
                }
                zip4j.setPassword(CbzCrypto.getDecryptedPasswordCbz())
                if (readerPreferences.cacheArchiveMangaOnDisk().get()) {
                    unzip()
                }
            } else {
                if (readerPreferences.cacheArchiveMangaOnDisk().get()) {
                    unzip()
                }
            }
        }
    }

    // SY <--
    override fun recycle() {
        super.recycle()
        zip?.close()
        // SY -->
        zip4j.close()
        tmpDir.deleteRecursively()
    }
    private fun unzip() {
        tmpDir.mkdirs()
        zip4j.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { zip4j.getInputStream(it) } }
            .forEach { entry ->
                zip4j.extractFile(entry, tmpDir.absolutePath)
            }
    }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        if (readerPreferences.cacheArchiveMangaOnDisk().get()) {
            return DirectoryPageLoader(UniFile.fromFile(tmpDir)!!).getPages()
        }

        if (zip == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                zip4j.charset = StandardCharsets.ISO_8859_1
            }

            return zip4j.fileHeaders.asSequence()
                .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { zip4j.getInputStream(it) } }
                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                .mapIndexed { i, entry ->
                    ReaderPage(i).apply {
                        stream = { zip4j.getInputStream(entry) }
                        status = Page.State.READY
                        zip4jFile = zip4j
                        zip4jEntry = entry
                    }
                }.toList()
        } else {
            // SY <--
            return zip.entries().asSequence()
                .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    ReaderPage(i).apply {
                        stream = { zip.getInputStream(entry) }
                        status = Page.State.READY
                    }
                }.toList()
        }
    }

    /**
     * No additional action required to load the page
     */
    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }
}
