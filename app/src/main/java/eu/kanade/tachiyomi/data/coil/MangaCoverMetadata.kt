package eu.kanade.tachiyomi.data.coil

import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.MangaCoverMetadata.setRatioAndColors
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import okio.BufferedSource
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Object that holds info about a covers size ratio + dominant colors
 * @author Jays2Kings
 */
object MangaCoverMetadata {
    private val preferences by injectLazy<LibraryPreferences>()
    private val coverCache by injectLazy<CoverCache>()

    fun load() {
        val ratios = preferences.coverRatios().get()
        MangaCover.coverRatioMap = ConcurrentHashMap(
            ratios.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val ratio = splits.lastOrNull()?.toFloatOrNull()
                if (id != null && ratio != null) {
                    id to ratio
                } else {
                    null
                }
            }.toMap(),
        )
        val colors = preferences.coverColors().get()
        MangaCover.dominantCoverColorMap = ConcurrentHashMap(
            colors.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val color = splits.getOrNull(1)?.toIntOrNull()
                val textColor = splits.getOrNull(2)?.toIntOrNull()
                if (id != null && color != null) {
                    id to (color to (textColor ?: 0))
                } else {
                    null
                }
            }.toMap(),
        )
    }

    /**
     * [setRatioAndColors] generate cover's color & ratio by reading cover's bitmap from [CoverCache].
     * It's called along with [MangaCoverFetcher.fetch] everytime a cover is **displayed** (anywhere).
     *
     * When called:
     *  - It removes saved ratio & [MangaCover.dominantCoverColors] if manga is not favorite.
     *  - It only reads the bitmap if [needsColorUpdate], otherwise it returns without touching ratio or colors.
     *  E.g. a favorite manga which already restored [MangaCover.dominantCoverColors] is skipped, unless its
     *  [MangaCover.vibrantCoverColor] is wanted too (not [onlyDominantColor]) and not loaded yet.
     *
     * Set [MangaCover.dominantCoverColors] and ratio for favorite manga only.
     * Set [MangaCover.vibrantCoverColor] for all mangas.
     *
     * @param bufferedSource if not null then it will load bitmap from [BufferedSource], regardless of [ogFile]
     * @param ogFile if not null then it will load bitmap from [File]. If it's null then it will try to load bitmap
     *  from [CoverCache] using either [CoverCache.customCoverCacheDir] or [CoverCache.cacheDir]
     * @param force if true then it will re-calculate ratio & colors even if they are known, except in the cases
     *  where [needsColorUpdate] always skips.
     *
     * This is only for loading color first time it appears on Library/Browse. Any new colors caused by loading new
     * cover when open a manga detail or change cover will be updated separately on [MangaScreenModel.setPaletteColor].
     *
     * @author Jays2Kings, cuong-tran
     */
    fun setRatioAndColors(
        mangaCover: MangaCover,
        bufferedSource: BufferedSource? = null,
        ogFile: File? = null,
        onlyDominantColor: Boolean = true,
        force: Boolean = false,
    ) {
        if (!mangaCover.isMangaFavorite) {
            mangaCover.remove()
        }

        // Ratio isn't used yet, so it's not worth decoding even the bounds when no color is needed
        if (!needsColorUpdate(mangaCover, onlyDominantColor, force)) return

        val options = BitmapFactory.Options().apply { inSampleSize = SUB_SAMPLE }

        val bitmap = if (bufferedSource != null) {
            bufferedSource.inputStream().use { inputStream ->
                BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options,
                )
            }
        } else {
            val file = ogFile
                ?: coverCache.getCustomCoverFile(mangaCover.mangaId).takeIf { it.exists() }
                ?: coverCache.getCoverFile(mangaCover.url)
            if (file == null || !file.exists()) return
            // if the file exists and there was still an error then the file is corrupted
            BitmapFactory.decodeFile(file.path, options)
        }

        bitmap?.let {
            Palette.from(it).generate { palette ->
                if (palette == null) return@generate
                if (mangaCover.isMangaFavorite) {
                    palette.dominantSwatch?.let { swatch ->
                        mangaCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                    }
                }
                val vibrantCoverColor = palette.getBestColor() ?: return@generate
                mangaCover.vibrantCoverColor = vibrantCoverColor
            }
        }
        if (mangaCover.isMangaFavorite && options.outWidth != -1 && options.outHeight != -1) {
            mangaCover.ratio = options.outWidth / options.outHeight.toFloat()
        }
    }

    /**
     * Whether [setRatioAndColors] would actually decode the cover's bitmap to extract colors:
     *  - Non-favorite manga: if it has no [MangaCover.vibrantCoverColor] yet, and that is wanted (not
     *  [onlyDominantColor]) or [force]d.
     *  - Favorite manga with [onlyDominantColor]: if it has no [MangaCover.dominantCoverColors] yet (ignores [force]).
     *  - Other favorite manga: if it misses either color, or if [force]d.
     *
     * Callers can use this to avoid opening or buffering a source which would never be read.
     */
    fun needsColorUpdate(
        mangaCover: MangaCover,
        onlyDominantColor: Boolean = true,
        force: Boolean = false,
    ): Boolean {
        return when {
            !mangaCover.isMangaFavorite -> mangaCover.vibrantCoverColor == null && (!onlyDominantColor || force)
            onlyDominantColor -> mangaCover.dominantCoverColors == null
            else -> mangaCover.dominantCoverColors == null || mangaCover.vibrantCoverColor == null || force
        }
    }

    fun MangaCover.remove() {
        MangaCover.coverRatioMap.remove(mangaId)
        MangaCover.dominantCoverColorMap.remove(mangaId)
    }

    fun savePrefs() {
        val mapCopy = MangaCover.coverRatioMap.toMap()
        preferences.coverRatios().set(mapCopy.map { "${it.key}|${it.value}" }.toSet())
        val mapColorCopy = MangaCover.dominantCoverColorMap.toMap()
        preferences.coverColors().set(mapColorCopy.map { "${it.key}|${it.value.first}|${it.value.second}" }.toSet())
    }

    private const val SUB_SAMPLE = 4
}
