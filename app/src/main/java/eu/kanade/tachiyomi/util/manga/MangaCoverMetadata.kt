package eu.kanade.tachiyomi.util.manga

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
     * [setRatioAndColors] generate cover's color & ratio.
     * It's called everytime while browsing to get manga's color from [CoverCache].
     *
     * It won't run if manga is not in library but already has [MangaCover.vibrantCoverColor].
     *
     * If manga already has [MangaCover.vibrantCoverColor] (wrote by previous run) and not in library,
     * it won't do anything. It only run with favorite manga, or non-favorite manga without color.
     *
     * It removes saved colors from saved Prefs of [MangaCover.coverColorMap] if manga is not favorite.
     *
     * If a favorite manga already restored [MangaCover.dominantCoverColors] then it
     * will skip actually reading bitmap, only extract ratio. Except when [MangaCover.vibrantCoverColor]
     * is not loaded then it will read bitmap & extract vibrant color.
     *
     * Set [MangaCover.dominantCoverColors] for favorite manga only.
     * Set [MangaCover.vibrantCoverColor] for all mangas.
     *
     * @author Jays2Kings
     */
    fun setRatioAndColors(
        mangaCover: MangaCover,
        bufferedSource: BufferedSource? = null,
        ogFile: File? = null,
        force: Boolean = false,
    ) {
        if (!mangaCover.isMangaFavorite) {
            mangaCover.remove()
        }
        // Won't do anything if manga is browsing & color loaded
        if (mangaCover.vibrantCoverColor != null && !mangaCover.isMangaFavorite) return
        val file = ogFile
            ?: coverCache.getCustomCoverFile(mangaCover.mangaId).takeIf { it.exists() }
            ?: coverCache.getCoverFile(mangaCover.url)

        val options = BitmapFactory.Options()
        val hasVibrantColor = if (mangaCover.isMangaFavorite) mangaCover.vibrantCoverColor != null else true
        // If dominantCoverColors is not null, it means that color is restored from Prefs
        // and also has vibrantCoverColor (e.g. new color caused by updated cover)
        val updateRatioOnly = mangaCover.dominantCoverColors != null && hasVibrantColor && !force
        if (updateRatioOnly) {
            // Just trying to update ratio without actual reading bitmap (bitmap will be null)
            // This is often when open a favorite manga
            options.inJustDecodeBounds = true
        } else {
            options.inSampleSize = 4
        }

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
        if (mangaCover.isMangaFavorite && !(options.outWidth == -1 || options.outHeight == -1)) {
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

/**
 * Calculate the best [Palette.Swatch] from [Palette]
 * @author Jays2Kings
 */
fun Palette.getBestColor(): Int? {
    // How big is the vibrant color
    val vibPopulation = vibrantSwatch?.population ?: -1
    // Saturation of the most dominant color
    val domSat = dominantSwatch?.hsl?.get(1) ?: 0f
    // Brightness of the most dominant color
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    // How big is the muted color
    val mutedPopulation = mutedSwatch?.population ?: -1
    // Saturation of the muted color
    val mutedSat = mutedSwatch?.hsl?.get(1) ?: 0f
    // If muted color is 3 times bigger than vibrant color then minimum acceptable saturation
    // for muted color is lower (more likely to use it even if it's not colorful)
    val mutedSatMinAcceptable = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f

    val dominantIsColorful = domSat >= .25f
    val dominantBrightnessJustRight = domLum <= .8f && domLum > .2f
    val vibrantIsConsiderableBigEnough = vibPopulation >= mutedPopulation * 0.75f
    val mutedIsBig = mutedPopulation > vibPopulation * 1.5f
    val mutedIsNotTooBoring = mutedSat > mutedSatMinAcceptable

    return when {
        dominantIsColorful && dominantBrightnessJustRight -> dominantSwatch
        // use vibrant color even if it's only 0.75 times smaller than muted color
        vibrantIsConsiderableBigEnough -> vibrantSwatch
        // use muted color if it's 1.5 times bigger than vibrant color and colorful enough (above the limit)
        mutedIsBig && mutedIsNotTooBoring -> mutedSwatch
        // return major vibrant color variant with more favor of vibrant color (size x3)
        else -> listOfNotNull(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch)
            .maxByOrNull {
                if (it === vibrantSwatch) vibPopulation * 3 else it.population
            }
    }?.rgb
}
