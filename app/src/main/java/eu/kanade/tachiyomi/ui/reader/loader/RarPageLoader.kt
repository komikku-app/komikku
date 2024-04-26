package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.os.Build
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
internal class RarPageLoader(file: UniFile) : PageLoader() {

    // SY -->
    private val tempFileManager: UniFileTempFileManager by injectLazy()

    private val rar = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        Archive(tempFileManager.createTempFile(file))
    } else {
        Archive(file.openInputStream())
    }

    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
    }

    init {
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            tmpDir.mkdirs()
            rar.fileHeaders.asSequence()
                .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { rar.getInputStream(it) } }
                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                .forEach { header ->
                    File(tmpDir, header.fileName.substringAfterLast("/"))
                        .also { it.createNewFile() }
                        .outputStream()
                        .use { output ->
                            rar.getInputStream(header).use { input ->
                                input.copyTo(output)
                            }
                        }
                }
        }
    }
    // SY <--

    override var isLocal: Boolean = true

    /**
     * Pool for copying compressed files to an input stream.
     */
    private val pool = Executors.newFixedThreadPool(1)

    override suspend fun getPages(): List<ReaderPage> {
        // SY -->
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            return DirectoryPageLoader(UniFile.fromFile(tmpDir)!!).getPages()
        }
        val mutex = Mutex()
        // SY <--
        return rar.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { rar.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                // SY -->
                val imageBytesDeferred: Deferred<ByteArray>? =
                    when (readerPreferences.archiveReaderMode().get()) {
                        ReaderPreferences.ArchiveReaderMode.LOAD_INTO_MEMORY -> {
                            CoroutineScope(Dispatchers.IO).async {
                                mutex.withLock {
                                    getStream(header).buffered().use { stream ->
                                        stream.readBytes()
                                    }
                                }
                            }
                        }

                        else -> null
                    }

                val imageBytes by lazy { runBlocking { imageBytesDeferred?.await() } }
                // SY <--
                ReaderPage(i).apply {
                    // SY -->
                    stream = { imageBytes?.copyOf()?.inputStream() ?: getStream(header) }
                    // SY <--
                    status = Page.State.READY
                }
            }
            .toList()
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        rar.close()
        // SY -->
        tmpDir.deleteRecursively()
        // SY <--
        pool.shutdown()
    }

    /**
     * Returns an input stream for the given [header].
     */
    private fun getStream(header: FileHeader): InputStream {
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        pool.execute {
            try {
                pipeOut.use {
                    rar.extractFile(header, it)
                }
            } catch (e: Exception) {
            }
        }
        return pipeIn
    }
}
