package app.komikku.desktop.ui.manga

import app.komikku.shared.coil.DesktopMangaCoverFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.storage.DiskUtil
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * Desktop cover actions of the details (P10), on Android's cover logic: [CoverCache]'s custom cover file, the borrowed
 * `Manga.editCover` (a local entry's cover through [LocalCoverManager], a library entry's custom cover in the cache) and
 * `deleteCustomCover`, each followed by `awaitUpdateCoverLastModified` so that the cover is loaded again (Android
 * `MangaCoverScreenModel`). Saving copies the image file itself (custom cover, local file, or Coil's disk cache after
 * loading it) instead of Android's re-encoded bitmap, so nothing is lost and animated covers stay animated.
 */
class MangaCoverActions(
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val localCoverManager: LocalCoverManager = Injekt.get(),
    private val imageLoader: () -> ImageLoader = { SingletonImageLoader.get(PlatformContext.INSTANCE) },
    /**
     * Whether this machine's image stack decodes the image file, the check before a picked file becomes the cover:
     * Skia as the cover loader uses it (Coil's Skia decoder), which may or may not decode AVIF / JPEG XL / HEIF
     * depending on the OS (P5). Replaceable in tests.
     */
    private val decodes: (File) -> Boolean = ::decodesWithSkia,
) {

    fun hasCustomCover(manga: Manga): Boolean = manga.hasCustomCover(coverCache)

    /** Like Android, a new cover only sticks to a library entry (custom cover) or a local entry (its cover file). */
    fun canEditCover(manga: Manga): Boolean = manga.favorite || manga.isLocal()

    /** The suggested file name for "Save": the title as a valid file name plus the image's own extension. */
    fun suggestedFileName(manga: Manga, extension: String?): String =
        DiskUtil.buildValidFilename(manga.title.ifBlank { "cover" }) + ".${extension ?: "jpg"}"

    /**
     * Runs [block] with the cover's image file as shown (custom cover, local file, or Coil's disk cache entry, loaded
     * first when needed); `null` when there is none. Not on the UI thread.
     */
    suspend fun <T> withCoverFile(manga: Manga, block: (File) -> T): T? {
        coverCache.getCustomCoverFile(manga.id).takeIf { it.exists() }?.let { return block(it) }
        val url = manga.thumbnailUrl ?: return null
        when {
            url.startsWith("file:") -> return File(URI(url)).takeIf { it.isFile }?.let(block)
            url.startsWith("/") || File(url).isAbsolute -> return File(url).takeIf { it.isFile }?.let(block)
        }
        val loader = imageLoader()
        val diskCache = loader.diskCache ?: return null
        val cover = manga.asMangaCover()
        // The key the cover fetcher stores the source's cover under
        val key = DesktopMangaCoverFetcher.diskCacheKey(cover)
        diskCache.openSnapshot(key)?.use { return block(it.data.toFile()) }
        // A cover can be in the memory cache only: load it again through the fetcher so that it lands on disk
        val request = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(cover)
            .memoryCachePolicy(CachePolicy.WRITE_ONLY)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        val result = loader.execute(request)
        if (result !is SuccessResult) return null
        return diskCache.openSnapshot(key)?.use { block(it.data.toFile()) }
    }

    /** The image type's extension of [file], `null` when it is not an image this app knows. */
    fun imageExtension(file: File): String? = file.inputStream().buffered().use { ImageUtil.findImageType(it) }?.extension

    /** Asks for the target with [chooseTarget] (given the suggested name) and copies the cover there; `false` when cancelled. */
    suspend fun saveCover(manga: Manga, chooseTarget: (suggestedName: String) -> File?): Boolean {
        val saved = withCoverFile(manga) { source ->
            val target = chooseTarget(suggestedFileName(manga, imageExtension(source))) ?: return@withCoverFile false
            source.copyTo(target, overwrite = true)
            true
        }
        return saved ?: throw IOException("No cover image for ${manga.title}")
    }

    /**
     * Android `MangaCoverScreenModel.editCover` with a file instead of a content URI. Throws, storing nothing, when the
     * entry cannot get a cover (Android's `editCover` would silently do nothing), when the file is no image this app
     * knows, or when this machine cannot decode it (e.g. AVIF / JPEG XL / HEIF without a codec, P5).
     */
    suspend fun setCover(manga: Manga, image: File) {
        if (!canEditCover(manga)) throw IOException("${manga.title} cannot get a custom cover: not in the library")
        if (imageExtension(image) == null) throw IOException("Not an image: ${image.name}")
        if (!decodes(image)) throw IOException("The image cannot be decoded on this machine: ${image.name}")
        image.inputStream().use { manga.editCover(localCoverManager, it, updateManga, coverCache) }
    }

    /** Android `MangaCoverScreenModel.deleteCustomCover`. */
    suspend fun deleteCustomCover(manga: Manga) {
        coverCache.deleteCustomCover(manga.id)
        updateManga.awaitUpdateCoverLastModified(manga.id)
    }

    companion object {
        /**
         * What "Edit cover" offers in the file chooser. AVIF / JPEG XL / HEIF stay offered since Skia may decode them
         * through the OS (macOS / Windows unverified, P5); a picked file is only accepted when it decodes ([setCover]).
         */
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl", "heif", "heic")

        /** Decodes [file] fully with Skia, like Coil's Skia decoder for covers; `false` on any failure. */
        fun decodesWithSkia(file: File): Boolean = try {
            Image.makeFromEncoded(file.readBytes()).use { image ->
                Bitmap.makeFromImage(image).use { it.width > 0 && it.height > 0 }
            }
        } catch (e: Exception) {
            false
        }
    }
}
