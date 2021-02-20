package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import eu.kanade.tachiyomi.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLConnection
import kotlin.math.abs

object ImageUtil {

    fun isImage(name: String, openStream: (() -> InputStream)? = null): Boolean {
        val contentType = try {
            URLConnection.guessContentTypeFromName(name)
        } catch (e: Exception) {
            null
        } ?: openStream?.let { findImageType(it)?.mime }
        return contentType?.startsWith("image/") ?: false
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        try {
            val bytes = ByteArray(8)

            val length = if (stream.markSupported()) {
                stream.mark(bytes.size)
                stream.read(bytes, 0, bytes.size).also { stream.reset() }
            } else {
                stream.read(bytes, 0, bytes.size)
            }

            if (length == -1) {
                return null
            }

            if (bytes.compareWith(charByteArrayOf(0xFF, 0xD8, 0xFF))) {
                return ImageType.JPG
            }
            if (bytes.compareWith(charByteArrayOf(0x89, 0x50, 0x4E, 0x47))) {
                return ImageType.PNG
            }
            if (bytes.compareWith("GIF8".toByteArray())) {
                return ImageType.GIF
            }
            if (bytes.compareWith("RIFF".toByteArray())) {
                return ImageType.WEBP
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun ByteArray.compareWith(magic: ByteArray): Boolean {
        return magic.indices.none { this[it] != magic[it] }
    }

    private fun charByteArrayOf(vararg bytes: Int): ByteArray {
        return ByteArray(bytes.size).apply {
            for (i in bytes.indices) {
                set(i, bytes[i].toByte())
            }
        }
    }

    enum class ImageType(val mime: String, val extension: String) {
        JPG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp")
    }

    /**
     * Check whether the image is a double image (width > height), return the result and original stream
     */
    fun isDoublePage(imageStream: InputStream): Pair<Boolean, InputStream> {
        val imageBytes = imageStream.readBytes()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        return Pair(options.outWidth > options.outHeight, ByteArrayInputStream(imageBytes))
    }

    /**
     * Extract the 'side' part from imageStream and return it as InputStream.
     */
    fun splitInHalf(imageStream: InputStream, side: Side): InputStream {
        val imageBytes = imageStream.readBytes()

        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val height = imageBitmap.height
        val width = imageBitmap.width

        val singlePage = Rect(0, 0, width / 2, height)

        val half = Bitmap.createBitmap(width / 2, height, Bitmap.Config.ARGB_8888)
        val part = when (side) {
            Side.RIGHT -> Rect(width - width / 2, 0, width, height)
            Side.LEFT -> Rect(0, 0, width / 2, height)
        }
        val canvas = Canvas(half)
        canvas.drawBitmap(imageBitmap, part, singlePage, null)
        val output = ByteArrayOutputStream()
        half.compress(Bitmap.CompressFormat.JPEG, 100, output)

        return ByteArrayInputStream(output.toByteArray())
    }

    /**
     * Split the image into left and right parts, then merge them into a new image.
     */
    fun splitAndMerge(imageStream: InputStream, upperSide: Side): InputStream {
        val imageBytes = imageStream.readBytes()

        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val height = imageBitmap.height
        val width = imageBitmap.width

        val result = Bitmap.createBitmap(width / 2, height * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // right -> upper
        val rightPart = when (upperSide) {
            Side.RIGHT -> Rect(width - width / 2, 0, width, height)
            Side.LEFT -> Rect(0, 0, width / 2, height)
        }
        val upperPart = Rect(0, 0, width / 2, height)
        canvas.drawBitmap(imageBitmap, rightPart, upperPart, null)
        // left -> bottom
        val leftPart = when (upperSide) {
            Side.LEFT -> Rect(width - width / 2, 0, width, height)
            Side.RIGHT -> Rect(0, 0, width / 2, height)
        }
        val bottomPart = Rect(0, height, width / 2, height * 2)
        canvas.drawBitmap(imageBitmap, leftPart, bottomPart, null)

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        return ByteArrayInputStream(output.toByteArray())
    }

    enum class Side {
        RIGHT, LEFT
    }

    // SY -->
    @Suppress("UNUSED_VARIABLE")
    fun autoSetBackground(image: Bitmap?, alwaysUseWhite: Boolean, context: Context): Drawable {
        val backgroundColor = if (alwaysUseWhite) Color.WHITE else {
            context.getResourceColor(R.attr.colorPrimary)
        }
        if (image == null) return ColorDrawable(backgroundColor)
        if (image.width < 50 || image.height < 50) {
            return ColorDrawable(backgroundColor)
        }
        val top = 5
        val bot = image.height - 5
        val left = (image.width * 0.0275).toInt()
        val right = image.width - left
        val midX = image.width / 2
        val midY = image.height / 2
        val offsetX = (image.width * 0.01).toInt()
        val offsetY = (image.height * 0.01).toInt()
        val topLeftIsDark = isDark(image.getPixel(left, top))
        val topRightIsDark = isDark(image.getPixel(right, top))
        val midLeftIsDark = isDark(image.getPixel(left, midY))
        val midRightIsDark = isDark(image.getPixel(right, midY))
        val topMidIsDark = isDark(image.getPixel(midX, top))
        val botLeftIsDark = isDark(image.getPixel(left, bot))
        val botRightIsDark = isDark(image.getPixel(right, bot))

        var darkBG = (topLeftIsDark && (botLeftIsDark || botRightIsDark || topRightIsDark || midLeftIsDark || topMidIsDark)) ||
            (topRightIsDark && (botRightIsDark || botLeftIsDark || midRightIsDark || topMidIsDark))

        if (!isWhite(image.getPixel(left, top)) && pixelIsClose(image.getPixel(left, top), image.getPixel(midX, top)) &&
            !isWhite(image.getPixel(midX, top)) && pixelIsClose(image.getPixel(midX, top), image.getPixel(right, top)) &&
            !isWhite(image.getPixel(right, top)) && pixelIsClose(image.getPixel(right, top), image.getPixel(right, bot)) &&
            !isWhite(image.getPixel(right, bot)) && pixelIsClose(image.getPixel(right, bot), image.getPixel(midX, bot)) &&
            !isWhite(image.getPixel(midX, bot)) && pixelIsClose(image.getPixel(midX, bot), image.getPixel(left, bot)) &&
            !isWhite(image.getPixel(left, bot)) && pixelIsClose(image.getPixel(left, bot), image.getPixel(left, top))
        ) {
            return ColorDrawable(image.getPixel(left, top))
        }

        if (isWhite(image.getPixel(left, top)).toInt() +
            isWhite(image.getPixel(right, top)).toInt() +
            isWhite(image.getPixel(left, bot)).toInt() +
            isWhite(image.getPixel(right, bot)).toInt() > 2
        ) {
            darkBG = false
        }

        var blackPixel = when {
            topLeftIsDark -> image.getPixel(left, top)
            topRightIsDark -> image.getPixel(right, top)
            botLeftIsDark -> image.getPixel(left, bot)
            botRightIsDark -> image.getPixel(right, bot)
            else -> backgroundColor
        }

        var overallWhitePixels = 0
        var overallBlackPixels = 0
        var topBlackStreak = 0
        var topWhiteStreak = 0
        var botBlackStreak = 0
        var botWhiteStreak = 0
        outer@ for (x in intArrayOf(left, right, left - offsetX, right + offsetX)) {
            var whitePixelsStreak = 0
            var whitePixels = 0
            var blackPixelsStreak = 0
            var blackPixels = 0
            var blackStreak = false
            var whiteStrak = false
            val notOffset = x == left || x == right
            for ((index, y) in (0 until image.height step image.height / 25).withIndex()) {
                val pixel = image.getPixel(x, y)
                val pixelOff = image.getPixel(x + (if (x < image.width / 2) -offsetX else offsetX), y)
                if (isWhite(pixel)) {
                    whitePixelsStreak++
                    whitePixels++
                    if (notOffset) {
                        overallWhitePixels++
                    }
                    if (whitePixelsStreak > 14) {
                        whiteStrak = true
                    }
                    if (whitePixelsStreak > 6 && whitePixelsStreak >= index - 1) {
                        topWhiteStreak = whitePixelsStreak
                    }
                } else {
                    whitePixelsStreak = 0
                    if (isDark(pixel) && isDark(pixelOff)) {
                        blackPixels++
                        if (notOffset) {
                            overallBlackPixels++
                        }
                        blackPixelsStreak++
                        if (blackPixelsStreak >= 14) {
                            blackStreak = true
                        }
                        continue
                    }
                }
                if (blackPixelsStreak > 6 && blackPixelsStreak >= index - 1) {
                    topBlackStreak = blackPixelsStreak
                }
                blackPixelsStreak = 0
            }
            if (blackPixelsStreak > 6) {
                botBlackStreak = blackPixelsStreak
            } else if (whitePixelsStreak > 6) {
                botWhiteStreak = whitePixelsStreak
            }
            when {
                blackPixels > 22 -> {
                    if (x == right || x == right + offsetX) {
                        blackPixel = when {
                            topRightIsDark -> image.getPixel(right, top)
                            botRightIsDark -> image.getPixel(right, bot)
                            else -> blackPixel
                        }
                    }
                    darkBG = true
                    overallWhitePixels = 0
                    break@outer
                }
                blackStreak -> {
                    darkBG = true
                    if (x == right || x == right + offsetX) {
                        blackPixel = when {
                            topRightIsDark -> image.getPixel(right, top)
                            botRightIsDark -> image.getPixel(right, bot)
                            else -> blackPixel
                        }
                    }
                    if (blackPixels > 18) {
                        overallWhitePixels = 0
                        break@outer
                    }
                }
                whiteStrak || whitePixels > 22 -> darkBG = false
            }
        }

        val topIsBlackStreak = topBlackStreak > topWhiteStreak
        val bottomIsBlackStreak = botBlackStreak > botWhiteStreak
        if (overallWhitePixels > 9 && overallWhitePixels > overallBlackPixels) {
            darkBG = false
        }
        if (topIsBlackStreak && bottomIsBlackStreak) {
            darkBG = true
        }
        if (darkBG) {
            return if (isWhite(image.getPixel(left, bot)) && isWhite(image.getPixel(right, bot))) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(blackPixel, blackPixel, backgroundColor, backgroundColor)
                )
            } else if (isWhite(image.getPixel(left, top)) && isWhite(image.getPixel(right, top))) {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(backgroundColor, backgroundColor, blackPixel, blackPixel)
                )
            } else ColorDrawable(blackPixel)
        }
        if (topIsBlackStreak || (
            topLeftIsDark && topRightIsDark &&
                isDark(image.getPixel(left - offsetX, top)) && isDark(image.getPixel(right + offsetX, top)) &&
                (topMidIsDark || overallBlackPixels > 9)
            )
        ) {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(blackPixel, blackPixel, backgroundColor, backgroundColor)
            )
        } else if (bottomIsBlackStreak || (
            botLeftIsDark && botRightIsDark &&
                isDark(image.getPixel(left - offsetX, bot)) && isDark(image.getPixel(right + offsetX, bot)) &&
                (isDark(image.getPixel(midX, bot)) || overallBlackPixels > 9)
            )
        ) {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(backgroundColor, backgroundColor, blackPixel, blackPixel)
            )
        }
        return ColorDrawable(backgroundColor)
    }

    private fun isDark(color: Int): Boolean {
        return Color.red(color) < 40 && Color.blue(color) < 40 && Color.green(color) < 40 &&
            Color.alpha(color) > 200
    }

    private fun isWhite(color: Int): Boolean {
        return Color.red(color) + Color.blue(color) + Color.green(color) > 740
    }

    private fun Boolean.toInt() = if (this) 1 else 0

    private fun pixelIsClose(color1: Int, color2: Int): Boolean {
        return abs(Color.red(color1) - Color.red(color2)) < 30 &&
            abs(Color.green(color1) - Color.green(color2)) < 30 &&
            abs(Color.blue(color1) - Color.blue(color2)) < 30
    }
    // SY <--
}
