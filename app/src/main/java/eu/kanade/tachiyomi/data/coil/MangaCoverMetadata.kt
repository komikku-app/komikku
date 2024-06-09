package eu.kanade.tachiyomi.data.coil

import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import eu.kanade.tachiyomi.data.cache.CoverCache
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
        MangaCover.coverColorMap = ConcurrentHashMap(
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
     *  - It removes saved colors from saved Prefs of [MangaCover.coverColorMap] if manga is not favorite.
     *  - If a favorite manga already restored [MangaCover.dominantCoverColors] then it
     * will skip actually reading bitmap, only extract ratio. Except when [MangaCover.vibrantCoverColor]
     * is not loaded then it will read bitmap & extract vibrant color.
     * => always set [force] to true so it will always re-calculate ratio & color.
     *
     * Set [MangaCover.dominantCoverColors] for favorite manga only.
     * Set [MangaCover.vibrantCoverColor] for all mangas.
     *
     * @param bufferedSource if not null then it will load bitmap from [BufferedSource], regardless of [ogFile]
     * @param ogFile if not null then it will load bitmap from [File]. If it's null then it will try to load bitmap
     *  from [CoverCache] using either [CoverCache.customCoverCacheDir] or [CoverCache.cacheDir]
     * @param force if true (default) then it will always re-calculate ratio & color for favorite mangas.
     *  This is useful when a favorite manga updates/changes its cover. If false then it will only update ratio.
     *
     * @author Jays2Kings
     */
    fun setRatioAndColors(
        mangaCover: MangaCover,
        bufferedSource: BufferedSource? = null,
        ogFile: File? = null,
        force: Boolean = true,
    ) {
        if (!mangaCover.isMangaFavorite) {
            mangaCover.remove()
            // For browsing mangas, it will only load color once.
            if (mangaCover.vibrantCoverColor != null) return
        }

        val options = BitmapFactory.Options()
        val hasVibrantColor = if (mangaCover.isMangaFavorite) mangaCover.vibrantCoverColor != null else true
        // If dominantCoverColors is not null, it means that color is restored from Prefs
        // and also has vibrantCoverColor (e.g. new color caused by updated cover)
        val updateRatioOnly = mangaCover.dominantCoverColors != null && hasVibrantColor && !force
        if (updateRatioOnly) {
            // Just trying to update ratio without actual reading bitmap (bitmap will be null)
            // This is often when open a favorite manga
            options.inJustDecodeBounds = true
            // Don't even need to update ratio because we don't use it yet.
            return
        } else {
            options.inSampleSize = 4
        }

        val file = ogFile
            ?: coverCache.getCustomCoverFile(mangaCover.mangaId).takeIf { it.exists() }
            ?: coverCache.getCoverFile(mangaCover.url)

        val bitmap = when {
            bufferedSource != null -> BitmapFactory.decodeStream(bufferedSource.inputStream(), null, options)
            // if the file exists and the there was still an error then the file is corrupted
            file?.exists() == true -> BitmapFactory.decodeFile(file.path, options)
            else -> { return }
        }

        if (bitmap != null) {
            Palette.from(bitmap).generate {
                if (it == null) return@generate
                if (mangaCover.isMangaFavorite) {
                    it.dominantSwatch?.let { swatch ->
                        mangaCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                    }
                }
                val color = it.getBestColor() ?: return@generate
                mangaCover.vibrantCoverColor = color
            }
        }
        if (mangaCover.isMangaFavorite && options.outWidth != -1 && options.outHeight != -1) {
            mangaCover.ratio = options.outWidth / options.outHeight.toFloat()
        }
    }

    fun MangaCover.remove() {
        MangaCover.coverRatioMap.remove(mangaId)
        MangaCover.coverColorMap.remove(mangaId)
    }

    fun savePrefs() {
        val mapCopy = MangaCover.coverRatioMap.toMap()
        preferences.coverRatios().set(mapCopy.map { "${it.key}|${it.value}" }.toSet())
        val mapColorCopy = MangaCover.coverColorMap.toMap()
        preferences.coverColors().set(mapColorCopy.map { "${it.key}|${it.value.first}|${it.value.second}" }.toSet())
    }
}
