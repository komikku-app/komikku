package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import com.jakewharton.disklrucache.DiskLruCache
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Response
import okio.buffer
import okio.sink
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * Class used to create page preview cache
 * For each page in a page preview list a file is created
 * For each page preview page a Json list is created and converted to a file.
 * The files are in format *md5key*.0
 *
 * @param context the application context.
 * @constructor creates an instance of the page preview cache.
 */
class PagePreviewCache(private val context: Context) {

    companion object {
        /** Name of cache directory.  */
        const val PARAMETER_CACHE_DIRECTORY = "page_preview_disk_cache"

        /** Application cache version.  */
        const val PARAMETER_APP_VERSION = 1

        /** The number of values per cache entry. Must be positive.  */
        const val PARAMETER_VALUE_COUNT = 1
    }

    /** Google Json class used for parsing JSON files.  */
    private val json: Json by injectLazy()

    /** Cache class used for cache management.  */
    private var diskCache = setupDiskCache(75)

    /**
     * Returns directory of cache.
     */
    private val cacheDir: File
        get() = diskCache.directory

    /**
     * Returns real size of directory.
     */
    private val realSize: Long
        get() = DiskUtil.getDirectorySize(cacheDir)

    /**
     * Returns real size of directory in human readable format.
     */
    val readableSize: String
        get() = Formatter.formatFileSize(context, realSize)

    // --> EH
    // Cache size is in MB
    private fun setupDiskCache(cacheSize: Long): DiskLruCache {
        return DiskLruCache.open(
            File(context.cacheDir, PARAMETER_CACHE_DIRECTORY),
            PARAMETER_APP_VERSION,
            PARAMETER_VALUE_COUNT,
            cacheSize * 1024 * 1024,
        )
    }
    // <-- EH

    /**
     * Get page list from cache.
     *
     * @param manga the manga.
     * @return the list of pages.
     */
    fun getPageListFromCache(manga: Manga, page: Int): PagePreviewPage {
        // Get the key for the manga.
        val key = DiskUtil.hashKeyForDisk(getKey(manga, page))

        // Convert JSON string to list of objects. Throws an exception if snapshot is null
        return diskCache.get(key).use {
            json.decodeFromString(it.getString(0))
        }
    }

    /**
     * Add page list to disk cache.
     *
     * @param manga the manga.
     * @param pages list of pages.
     */
    fun putPageListToCache(manga: Manga, pages: PagePreviewPage) {
        // Convert list of pages to json string.
        val cachedValue = json.encodeToString(pages)

        // Initialize the editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(getKey(manga, pages.page))
            editor = diskCache.edit(key) ?: return

            // Write page preview urls to cache.
            editor.newOutputStream(0).sink().buffer().use {
                it.write(cachedValue.toByteArray())
                it.flush()
            }

            diskCache.flush()
            editor.commit()
            editor.abortUnlessCommitted()
        } catch (e: Exception) {
            // Ignore.
        } finally {
            editor?.abortUnlessCommitted()
        }
    }

    /**
     * Returns true if page is in cache.
     *
     * @param imageUrl url of page.
     * @return true if in cache otherwise false.
     */
    fun isImageInCache(imageUrl: String): Boolean {
        return try {
            diskCache.get(DiskUtil.hashKeyForDisk(imageUrl)) != null
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Get page file from url.
     *
     * @param imageUrl url of page.
     * @return path of page.
     */
    fun getImageFile(imageUrl: String): File {
        // Get file from md5 key.
        val imageName = DiskUtil.hashKeyForDisk(imageUrl) + ".0"
        return File(diskCache.directory, imageName)
    }

    /**
     * Add page to cache.
     *
     * @param imageUrl url of page.
     * @param response http response from page.
     * @throws IOException page error.
     */
    @Throws(IOException::class)
    fun putImageToCache(imageUrl: String, response: Response) {
        // Initialize editor (edits the values for an entry).
        var editor: DiskLruCache.Editor? = null

        try {
            // Get editor from md5 key.
            val key = DiskUtil.hashKeyForDisk(imageUrl)
            editor = diskCache.edit(key) ?: throw IOException("Unable to edit key")

            // Get OutputStream and write page with Okio.
            response.body!!.source().saveTo(editor.newOutputStream(0))

            diskCache.flush()
            editor.commit()
        } finally {
            response.body?.close()
            editor?.abortUnlessCommitted()
        }
    }

    fun clear(): Int {
        var deletedFiles = 0
        cacheDir.listFiles()?.forEach {
            if (removeFileFromCache(it.name)) {
                deletedFiles++
            }
        }
        return deletedFiles
    }

    /**
     * Remove file from cache.
     *
     * @param file name of file "md5.0".
     * @return status of deletion for the file.
     */
    private fun removeFileFromCache(file: String): Boolean {
        // Make sure we don't delete the journal file (keeps track of cache).
        if (file == "journal" || file.startsWith("journal.")) {
            return false
        }

        return try {
            // Remove the extension from the file to get the key of the cache
            val key = file.substringBeforeLast(".")
            // Remove file from cache.
            diskCache.remove(key)
        } catch (e: Exception) {
            false
        }
    }

    private fun getKey(manga: Manga, page: Int): String {
        return "${manga.id}_$page"
    }
}
