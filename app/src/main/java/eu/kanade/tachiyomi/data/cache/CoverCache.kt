package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.vault.VaultSession
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
        // KMK -->
        private const val VAULT_COVERS_DIR = "private_vault/covers"
        private const val VAULT_CUSTOM_COVERS_DIR = "private_vault/covers/custom"
        // KMK <--
    }

    /**
     * Cache directory used for cache management.
     */
    // KMK -->
    private val cacheDir = getCacheDir(if (VaultSession.isActive) VAULT_COVERS_DIR else COVERS_DIR)

    private val customCoverCacheDir = getCacheDir(
        if (VaultSession.isActive) VAULT_CUSTOM_COVERS_DIR else CUSTOM_COVERS_DIR,
    )
    // KMK <--

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
