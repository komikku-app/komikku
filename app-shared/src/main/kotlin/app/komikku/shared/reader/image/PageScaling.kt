package app.komikku.shared.reader.image

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * KMK: how a page image is fitted into the reader viewport. Values follow Android's
 * `ReaderPreferences.imageScaleType()` (the `SubsamplingScaleImageView.SCALE_TYPE_*` constants), so
 * the preference means the same on both platforms.
 */
enum class PageFit(val preferenceValue: Int) {
    /** Whole page visible (Android: fit screen / center inside). */
    Screen(1),

    /** Fill the viewport, cropping the overflow (Android: stretch / center crop). */
    Stretch(2),
    Width(3),
    Height(4),

    /** 1 image pixel = 1 layout pixel. */
    Original(5),

    /** Width for tall pages, screen otherwise (Android: smart fit). */
    Smart(6),
    ;

    companion object {
        fun fromPreference(value: Int): PageFit = entries.find { it.preferenceValue == value } ?: Screen
    }
}

/** A width × height pair in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val aspect: Float get() = if (height == 0) 0f else width.toFloat() / height
}

/**
 * KMK: platform-neutral sizing math of the reader: the displayed size of a page for a [PageFit], the
 * size to decode it at (never larger than the image or than needed on screen, so memory stays bounded),
 * and the horizontal strips a tall (webtoon) image is cut into so no single bitmap gets too large.
 */
object PageScaling {

    /** Default maximum strip height: well under GPU texture limits and ~16 MB per strip at 1024 px width. */
    const val DEFAULT_MAX_TILE_HEIGHT = 4096

    /** Smart fit treats pages at least this much taller than the viewport's aspect as strips. */
    private const val SMART_TALL_FACTOR = 1.5f

    /** The page's size in layout pixels at zoom 1 inside [viewport]. */
    fun displaySize(image: PixelSize, viewport: PixelSize, fit: PageFit): Pair<Float, Float> {
        if (image.isEmpty || viewport.isEmpty) return 0f to 0f
        val scaleW = viewport.width.toFloat() / image.width
        val scaleH = viewport.height.toFloat() / image.height
        val scale = when (fit) {
            PageFit.Screen -> min(scaleW, scaleH)
            PageFit.Stretch -> max(scaleW, scaleH)
            PageFit.Width -> scaleW
            PageFit.Height -> scaleH
            PageFit.Original -> 1f
            PageFit.Smart -> if (image.aspect * SMART_TALL_FACTOR < viewport.aspect) scaleW else min(scaleW, scaleH)
        }
        return image.width * scale to image.height * scale
    }

    /**
     * The size to decode [image] at so it looks sharp at [displayWidth] layout pixels times
     * [maxZoom], without upscaling and without exceeding [maxPixels] (keeps aspect ratio).
     */
    fun decodeSize(image: PixelSize, displayWidth: Float, maxZoom: Float = 1f, maxPixels: Long = Long.MAX_VALUE): PixelSize {
        if (image.isEmpty) return image
        var width = min(image.width.toFloat(), max(1f, ceil(displayWidth * maxZoom)))
        val pixelsAtWidth = width.toDouble() * (width / image.aspect)
        if (pixelsAtWidth > maxPixels) width = kotlin.math.sqrt(maxPixels * image.aspect.toDouble()).toFloat()
        val w = max(1, width.roundToInt())
        val h = max(1, (w / image.aspect).roundToInt())
        return PixelSize(w, h)
    }

    /** Row ranges `[top, bottom)` cutting [height] into strips of at most [maxTileHeight]. */
    fun tiles(height: Int, maxTileHeight: Int = DEFAULT_MAX_TILE_HEIGHT): List<IntRange> {
        require(maxTileHeight > 0) { "maxTileHeight must be positive" }
        if (height <= 0) return emptyList()
        return (0 until height step maxTileHeight).map { top -> top until min(height, top + maxTileHeight) }
    }

    /**
     * The source rows of the original image that a decoded strip `[top, bottom)` covers, for an image
     * of [sourceHeight] rows decoded at [decodedHeight] rows.
     */
    fun sourceRows(tile: IntRange, decodedHeight: Int, sourceHeight: Int): Pair<Float, Float> {
        val ratio = sourceHeight.toFloat() / decodedHeight
        return tile.first * ratio to (tile.last + 1) * ratio
    }
}
