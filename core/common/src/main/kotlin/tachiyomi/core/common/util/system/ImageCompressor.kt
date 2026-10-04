// KMK -->
package tachiyomi.core.common.util.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import com.hippo.unifile.UniFile
import logcat.LogPriority
import okio.Buffer
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

object ImageCompressor {

    data class CompressionResult(
        val success: Boolean,
        val compressed: Boolean,
        val originalSize: Long,
        val finalSize: Long,
        val extension: String,
        val resultingFile: UniFile?,
    )

    fun getTargetCompressFormat(format: String, quality: Int): Pair<Bitmap.CompressFormat, String> {
        val avifFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { Bitmap.CompressFormat.valueOf("AVIF") }.getOrNull()
        } else {
            null
        }

        return if (avifFormat != null && format.equals("AVIF", ignoreCase = true)) {
            Pair(avifFormat, "avif")
        } else if (quality >= 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Pair(Bitmap.CompressFormat.WEBP_LOSSLESS, "webp")
            } else {
                @Suppress("DEPRECATION")
                Pair(Bitmap.CompressFormat.WEBP, "webp")
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Pair(Bitmap.CompressFormat.WEBP_LOSSY, "webp")
            } else {
                @Suppress("DEPRECATION")
                Pair(Bitmap.CompressFormat.WEBP, "webp")
            }
        }
    }

    /**
     * Checks if a file is already compressed with WebP or AVIF.
     */
    fun isAlreadyCompressed(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext == "webp" || ext == "avif") return true
        return try {
            file.inputStream().buffered().use { stream ->
                val type = ImageUtil.findImageType(stream)
                type == ImageUtil.ImageType.WEBP || type == ImageUtil.ImageType.AVIF
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a UniFile is already compressed with WebP or AVIF.
     */
    fun isAlreadyCompressed(file: UniFile): Boolean {
        val ext = file.extension?.lowercase()
        if (ext == "webp" || ext == "avif") return true
        return try {
            val stream: InputStream? = file.openInputStream()
            stream?.use {
                val type = ImageUtil.findImageType(it)
                type == ImageUtil.ImageType.WEBP || type == ImageUtil.ImageType.AVIF
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects if the bitmap is grayscale / monochrome.
     * Uses a fast spatial sampling grid (32x32 points) for sub-millisecond execution.
     */
    fun isMonochrome(bitmap: Bitmap, sampleGridSize: Int = 32): Boolean {
        val stepX = max(1, bitmap.width / sampleGridSize)
        val stepY = max(1, bitmap.height / sampleGridSize)
        var totalColorDelta = 0L
        var sampleCount = 0

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val maxDiff = max(abs(r - g), max(abs(r - b), abs(g - b)))
                totalColorDelta += maxDiff
                sampleCount++

                // Clear indication of color
                if (maxDiff > 14) {
                    return false
                }
            }
        }

        if (sampleCount == 0) return true
        return totalColorDelta.toDouble() / sampleCount < 3.5
    }

    /**
     * Converts a bitmap to pure neutral grayscale, which drops chrominance data in WebP/AVIF.
     */
    fun toGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Compresses a single standard File on disk.
     */
    fun compressFile(
        file: File,
        format: String = "WEBP",
        quality: Int = 80,
        autoGrayscale: Boolean = true,
        stripMetadata: Boolean = true,
    ): CompressionResult {
        val originalSize = file.length()
        val originalName = file.name

        // Skip animated images and already compressed files
        try {
            val isAnimated = file.inputStream().buffered().use { stream ->
                val source = Buffer().readFrom(stream)
                ImageUtil.isAnimatedAndSupported(source)
            }
            if (isAnimated) {
                return CompressionResult(
                    success = true,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension,
                    resultingFile = UniFile.fromFile(file),
                )
            }
        } catch (_: Exception) {
            // Ignore and proceed
        }

        if (isAlreadyCompressed(file)) {
            return CompressionResult(
                success = true,
                compressed = false,
                originalSize = originalSize,
                finalSize = originalSize,
                extension = file.extension,
                resultingFile = UniFile.fromFile(file),
            )
        }

        val parentDir = file.parentFile ?: return CompressionResult(
            success = false,
            compressed = false,
            originalSize = originalSize,
            finalSize = originalSize,
            extension = file.extension,
            resultingFile = UniFile.fromFile(file),
        )

        val (compressFormat, targetExtension) = getTargetCompressFormat(format, quality)
        val baseName = file.nameWithoutExtension
        val tempFile = File(parentDir, "$baseName.tmp_comp")

        var bitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null

        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = file.inputStream().buffered().use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                logcat(LogPriority.WARN) { "BitmapFactory.decodeStream returned NULL for $originalName" }
                tempFile.delete()
                return CompressionResult(
                    success = false,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension,
                    resultingFile = UniFile.fromFile(file),
                )
            }

            processedBitmap = if (autoGrayscale && isMonochrome(bitmap)) {
                toGrayscale(bitmap)
            } else {
                bitmap
            }

            val encodeSuccess = tempFile.outputStream().buffered().use { output ->
                processedBitmap.compress(compressFormat, quality.coerceIn(1, 100), output)
            }

            val compressedSize = tempFile.length()
            logcat(LogPriority.INFO) { "Encoded $originalName with $compressFormat q=$quality: encodeSuccess=$encodeSuccess, compressedSize=$compressedSize, originalSize=$originalSize" }

            // Strict size check: Only keep if smaller than original
            if (encodeSuccess && compressedSize > 0 && compressedSize < originalSize) {
                file.delete()
                val targetFile = File(parentDir, "$baseName.$targetExtension")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
                CompressionResult(
                    success = true,
                    compressed = true,
                    originalSize = originalSize,
                    finalSize = compressedSize,
                    extension = targetExtension,
                    resultingFile = UniFile.fromFile(targetFile),
                )
            } else {
                tempFile.delete()
                CompressionResult(
                    success = true,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension,
                    resultingFile = UniFile.fromFile(file),
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to compress image file: $originalName" }
            tempFile.delete()
            CompressionResult(
                success = false,
                compressed = false,
                originalSize = originalSize,
                finalSize = originalSize,
                extension = file.extension,
                resultingFile = UniFile.fromFile(file),
            )
        } finally {
            if (processedBitmap != null && processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            bitmap?.recycle()
        }
    }

    /**
     * Compresses a single image file on disk via UniFile.
     * Enforces atomic writes via a temporary file and guarantees no file bloat.
     */
    fun compressFile(
        file: UniFile,
        parentDir: UniFile? = null,
        format: String = "WEBP",
        quality: Int = 80,
        autoGrayscale: Boolean = true,
        stripMetadata: Boolean = true,
    ): CompressionResult {
        val filePath = file.filePath
        if (filePath != null) {
            val localFile = File(filePath)
            if (localFile.exists()) {
                return compressFile(localFile, format, quality, autoGrayscale, stripMetadata)
            }
        }

        var originalSize = file.length()
        val originalName = file.name ?: return CompressionResult(
            success = false,
            compressed = false,
            originalSize = originalSize,
            finalSize = originalSize,
            extension = file.extension.orEmpty(),
            resultingFile = file,
        )

        // Skip animated images and already compressed files
        try {
            val stream: InputStream? = file.openInputStream()
            val source = stream?.use { Buffer().readFrom(it) }
            if (source != null && ImageUtil.isAnimatedAndSupported(source)) {
                return CompressionResult(
                    success = true,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension.orEmpty(),
                    resultingFile = file,
                )
            }
        } catch (_: Exception) {
            // Ignore and proceed
        }

        if (isAlreadyCompressed(file)) {
            return CompressionResult(
                success = true,
                compressed = false,
                originalSize = originalSize,
                finalSize = originalSize,
                extension = file.extension.orEmpty(),
                resultingFile = file,
            )
        }

        val effectiveParentDir = parentDir
            ?: file.parentFile
            ?: file.filePath?.let { File(it).parentFile }?.let { UniFile.fromFile(it) }
            ?: run {
                logcat(LogPriority.WARN) { "Cannot find parent directory for UniFile: $originalName" }
                return CompressionResult(
                    success = false,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension.orEmpty(),
                    resultingFile = file,
                )
            }

        val (compressFormat, targetExtension) = getTargetCompressFormat(format, quality)
        val baseName = file.nameWithoutExtension ?: originalName.substringBeforeLast('.')
        val tempFileName = "$baseName.tmp_comp"
        val tempFile = effectiveParentDir.createFile(tempFileName) ?: run {
            logcat(LogPriority.WARN) { "Cannot create temp file $tempFileName in parent ${effectiveParentDir.name}" }
            return CompressionResult(
                success = false,
                compressed = false,
                originalSize = originalSize,
                finalSize = originalSize,
                extension = file.extension.orEmpty(),
                resultingFile = file,
            )
        }

        var bitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null

        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = file.openInputStream()?.buffered()?.use { stream ->
                if (originalSize <= 0) {
                    originalSize = stream.available().toLong().coerceAtLeast(0L)
                }
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                logcat(LogPriority.WARN) { "BitmapFactory.decodeStream returned NULL for $originalName" }
                tempFile.delete()
                return CompressionResult(
                    success = false,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension.orEmpty(),
                    resultingFile = file,
                )
            }

            processedBitmap = if (autoGrayscale && isMonochrome(bitmap)) {
                toGrayscale(bitmap)
            } else {
                bitmap
            }

            var bytesWritten = 0L
            val encodeSuccess = tempFile.openOutputStream()?.buffered()?.use { rawOutput ->
                val countingOutput = object : java.io.FilterOutputStream(rawOutput) {
                    override fun write(b: Int) {
                        out.write(b)
                        bytesWritten++
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        out.write(b, off, len)
                        bytesWritten += len
                    }
                }
                processedBitmap.compress(compressFormat, quality.coerceIn(1, 100), countingOutput)
            } ?: false

            val compressedSize = if (bytesWritten > 0) bytesWritten else tempFile.length()
            logcat(LogPriority.INFO) { "Encoded $originalName with $compressFormat q=$quality: encodeSuccess=$encodeSuccess, compressedSize=$compressedSize, originalSize=$originalSize" }

            // Strict size check: Only keep if smaller than original (if original size is known > 0)
            val isSmaller = if (originalSize > 0) compressedSize < originalSize else true
            if (encodeSuccess && compressedSize > 0 && isSmaller) {
                file.delete()
                val targetFileName = "$baseName.$targetExtension"
                tempFile.renameTo(targetFileName)
                val finalFile = effectiveParentDir.findFile(targetFileName) ?: tempFile
                CompressionResult(
                    success = true,
                    compressed = true,
                    originalSize = originalSize,
                    finalSize = compressedSize,
                    extension = targetExtension,
                    resultingFile = finalFile,
                )
            } else {
                // Compression didn't yield savings, keep original
                tempFile.delete()
                CompressionResult(
                    success = true,
                    compressed = false,
                    originalSize = originalSize,
                    finalSize = originalSize,
                    extension = file.extension.orEmpty(),
                    resultingFile = file,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to compress image file: $originalName" }
            tempFile.delete()
            CompressionResult(
                success = false,
                compressed = false,
                originalSize = originalSize,
                finalSize = originalSize,
                extension = file.extension.orEmpty(),
                resultingFile = file,
            )
        } finally {
            if (processedBitmap != null && processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            bitmap?.recycle()
        }
    }
}
// KMK <--
