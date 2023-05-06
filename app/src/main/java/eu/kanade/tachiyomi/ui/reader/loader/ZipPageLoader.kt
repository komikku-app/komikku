package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import net.lingala.zip4j.ZipFile
import tachiyomi.core.util.system.ImageUtil
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
class ZipPageLoader(
    file: File,
    // SY -->
    context: Context,
    // SY <--
) : PageLoader() {

    /**
     * The zip file to load pages from.
     */
    // SY -->
    private var zip4j = ZipFile(file)

    init {
        if (zip4j.isEncrypted) {
            if (!CbzCrypto.checkCbzPassword(zip4j, CbzCrypto.getDecryptedPasswordCbz())) {
                this.recycle()
                throw Exception(context.getString(R.string.wrong_cbz_archive_password))
            }
        }
    }

    private val zip: java.util.zip.ZipFile? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!zip4j.isEncrypted) java.util.zip.ZipFile(file, StandardCharsets.ISO_8859_1) else null
        } else {
            if (!zip4j.isEncrypted) java.util.zip.ZipFile(file) else null
        }
    // SY <--

    /**
     * Recycles this loader and the open zip.
     */
    override fun recycle() {
        super.recycle()
        // SY -->
        zip4j.close()
        zip?.close()
        // SY <--
    }

    /**
     * Returns the pages found on this zip archive ordered with a natural comparator.
     */
    override suspend fun getPages(): List<ReaderPage> {
        // SY -->
        // Part can be removed after testing that there are no bugs with zip4j on some users devices
        if (zip != null) {
            // SY <--
            return zip.entries().asSequence()
                .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    ReaderPage(i).apply {
                        stream = { zip.getInputStream(entry) }
                        status = Page.State.READY
                    }
                    // SY -->
                }.toList()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                zip4j.charset = StandardCharsets.ISO_8859_1
            }
            zip4j.setPassword(CbzCrypto.getDecryptedPasswordCbz())

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
        }
        // SY <--
    }

    /**
     * No additional action required to load the page
     */
    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }
}
