package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.os.Build
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.zip.ZipFile
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.core.common.storage.isEncryptedZip
import tachiyomi.core.common.storage.openReadOnlyChannel
import tachiyomi.core.common.storage.testCbzPassword
import tachiyomi.core.common.storage.unzip
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.channels.SeekableByteChannel
import net.lingala.zip4j.ZipFile as Zip4jFile

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: UniFile, context: Context) : PageLoader() {

    // SY -->
    private val channel: SeekableByteChannel = file.openReadOnlyChannel(context)
    private val tempFileManager: UniFileTempFileManager by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
    }

    private val apacheZip: ZipFile? = if (!file.isEncryptedZip() && Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
        ZipFile.Builder()
            .setSeekableByteChannel(channel)
            .get()
    } else {
        null
    }

    private val tmpFile =
        if (
            apacheZip == null &&
            readerPreferences.archiveReaderMode().get() != ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK
        ) {
            tempFileManager.createTempFile(file)
        } else {
            null
        }

    private val zip4j =
        if (apacheZip == null && tmpFile != null) {
            Zip4jFile(tmpFile)
        } else {
            null
        }

    init {
        if (file.isEncryptedZip()) {
            if (!file.testCbzPassword()) {
                this.recycle()
                throw IllegalStateException(context.stringResource(SYMR.strings.wrong_cbz_archive_password))
            }
            zip4j?.setPassword(CbzCrypto.getDecryptedPasswordCbz())
        }
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            file.unzip(tmpDir, onlyCopyImages = true)
        }
    }

    // SY <--
    override fun recycle() {
        super.recycle()
        apacheZip?.close()
        // SY -->
        zip4j?.close()
        tmpDir.deleteRecursively()
    }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            return DirectoryPageLoader(UniFile.fromFile(tmpDir)!!).getPages()
        }
        return if (apacheZip == null) {
            loadZip4j()
        } else {
            loadApacheZip(apacheZip)
        }
    }

    private fun loadZip4j(): List<ReaderPage> {
        val mutex = Mutex()
        return zip4j!!.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { zip4j.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, entry ->
                val imageBytesDeferred: Deferred<ByteArray>? =
                    when (readerPreferences.archiveReaderMode().get()) {
                        ReaderPreferences.ArchiveReaderMode.LOAD_INTO_MEMORY -> {
                            CoroutineScope(Dispatchers.IO).async {
                                mutex.withLock {
                                    zip4j.getInputStream(entry).buffered().use { stream ->
                                        stream.readBytes()
                                    }
                                }
                            }
                        }

                        else -> null
                    }
                val imageBytes by lazy { runBlocking { imageBytesDeferred?.await() } }
                ReaderPage(i).apply {
                    stream = { imageBytes?.copyOf()?.inputStream() ?: zip4j.getInputStream(entry) }
                    status = Page.State.READY
                }
            }.toList()
    }

    private fun loadApacheZip(zip: ZipFile): List<ReaderPage> {
        val mutex = Mutex()
        return zip.entries.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                val imageBytesDeferred: Deferred<ByteArray>? =
                    when (readerPreferences.archiveReaderMode().get()) {
                        ReaderPreferences.ArchiveReaderMode.LOAD_INTO_MEMORY -> {
                            CoroutineScope(Dispatchers.IO).async {
                                mutex.withLock {
                                    zip.getInputStream(entry).buffered().use { stream ->
                                        stream.readBytes()
                                    }
                                }
                            }
                        }

                        else -> null
                    }
                val imageBytes by lazy { runBlocking { imageBytesDeferred?.await() } }
                ReaderPage(i).apply {
                    stream = { imageBytes?.copyOf()?.inputStream() ?: zip.getInputStream(entry) }
                    status = Page.State.READY
                }
            }.toList()
    }
    // SY <--

    /**
     * No additional action required to load the page
     */
    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }
}
