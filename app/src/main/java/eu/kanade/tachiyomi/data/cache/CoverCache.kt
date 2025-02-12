package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.format.Formatter
import coil3.imageLoader
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.manga.model.Manga
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
        private const val ONLINE_COVERS_DIR = "online_covers"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = getCacheDir(COVERS_DIR)

    /** Cache directory used for custom cover cache management. */
    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    /** Cache directory used for covers not in library management. */
    private val onlineCoverDirectory =
        File(context.cacheDir, ONLINE_COVERS_DIR).also { it.mkdirs() }

    private val maxOnlineCacheSize = 50L * 1024L * 1024L // 50 MB

    private var lastClean = 0L

    fun getCoverCacheSize(): String {
        return Formatter.formatFileSize(context, DiskUtil.getDirectorySize(cacheDir))
    }

    fun getOnlineCoverCacheSize(): String {
        return Formatter.formatFileSize(context, DiskUtil.getDirectorySize(onlineCoverDirectory))
    }

    suspend fun deleteOldCovers() {
//        val db = Injekt.get<DatabaseHelper>()
//        var deletedSize = 0L
//        val urls =
//            db.getFavoriteMangaList().executeOnIO().mapNotNull {
//                it.thumbnail_url?.let { url ->
//                    return@mapNotNull DiskUtil.hashKeyForDisk(url)
//                }
//                null
//            }
//        val files = cacheDir.listFiles()?.iterator() ?: return
//        while (files.hasNext()) {
//            val file = files.next()
//            if (file.isFile && file.name !in urls) {
//                deletedSize += file.length()
//                file.delete()
//            }
//        }
//
//        withUIContext {
//            context.toast(
//                context.getString(R.string.deleted_, Formatter.formatFileSize(context, deletedSize))
//            )
//        }
    }

    /** Clear out online covers */
    suspend fun deleteAllCachedCovers() {
        val directory = onlineCoverDirectory
        var deletedSize = 0L
        val files = directory.listFiles()?.sortedBy { it.lastModified() }?.iterator() ?: return
        while (files.hasNext()) {
            val file = files.next()
            deletedSize += file.length()
            file.delete()
        }
//        withContext(Dispatchers.Main) {
//            context.toast(
//                context.getString(R.string.deleted_, Formatter.formatFileSize(context, deletedSize))
//            )
//        }
        context.imageLoader.memoryCache?.clear()
        // CoilDiskCache.get(context).clear()

        lastClean = System.currentTimeMillis()
    }

//    /** Clear out online covers until its under a certain size */
//    suspend fun deleteCachedCovers() {
//        withIOContext {
//            if (lastClean + renewInterval < System.currentTimeMillis()) {
//                try {
//                    val directory = onlineCoverDirectory
//                    val size = DiskUtil.getDirectorySize(directory)
//                    if (size <= maxOnlineCacheSize) {
//                        return@withIOContext
//                    }
//                    var deletedSize = 0L
//                    val files =
//                        directory.listFiles()?.sortedBy { it.lastModified() }?.iterator()
//                            ?: return@withIOContext
//                    while (files.hasNext()) {
//                        val file = files.next()
//                        deletedSize += file.length()
//                        file.delete()
//                        if (size - deletedSize <= maxOnlineCacheSize) {
//                            break
//                        }
//                    }
//                } catch (e: Exception) {
//                    TimberKt.e(e)
//                }
//                lastClean = System.currentTimeMillis()
//            }
//        }
//    }

    /**
     * Returns the cover from cache.
     *
     * @param mangaThumbnailUrl thumbnail url for the manga.
     * @return cover image.
     */
    fun getCoverFile(mangaThumbnailUrl: String?): File? {
        return mangaThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Returns the custom cover from cache.
     *
     * @param mangaId the manga id.
     * @return cover image.
     */
    fun getCustomCoverFile(mangaId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(mangaId.toString()))
    }

    /**
     * Saves the given stream as the manga's custom cover to cache.
     *
     * @param manga the manga.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        getCustomCoverFile(manga.id).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    /**
     * Delete the cover files of the manga from the cache.
     *
     * @param manga the manga.
     * @param deleteCustomCover whether the custom cover should be deleted.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(manga: Manga, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(manga.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCover(manga.id)) ++deleted
        }

        return deleted
    }

    /**
     * Delete custom cover of the manga from the cache
     *
     * @param mangaId the manga id.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(mangaId: Long?): Boolean {
        return getCustomCoverFile(mangaId).let {
            it.exists() && it.delete()
        }
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
