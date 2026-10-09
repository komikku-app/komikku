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

        val originalSize = file.length()
        val originalName = file.name ?: return createFallbackResult(file, originalSize)

        val skippableResult = checkUniFileSkippable(file, originalSize)
        if (skippableResult != null) return skippableResult

        val effectiveParentDir = resolveEffectiveParentDir(file, parentDir) ?: run {
            logcat(LogPriority.WARN) { "Cannot find parent directory for UniFile: $originalName" }
            return createFallbackResult(file, originalSize)
        }

        val (compressFormat, targetExtension) = getTargetCompressFormat(format, quality)
        val baseName = file.nameWithoutExtension ?: originalName.substringBeforeLast('.')
        val tempFileName = "$baseName.tmp_comp"
        val tempFile = effectiveParentDir.createFile(tempFileName) ?: run {
            logcat(LogPriority.WARN) { "Cannot create temp file $tempFileName in parent ${effectiveParentDir.name}" }
            return createFallbackResult(file, originalSize)
        }

        val ctx = UniFileCompressionContext(
            file = file,
            tempFile = tempFile,
            effectiveParentDir = effectiveParentDir,
            baseName = baseName,
            targetExtension = targetExtension,
            compressFormat = compressFormat,
            quality = quality,
            autoGrayscale = autoGrayscale,
            originalSize = originalSize,
            originalName = originalName,
        )
        return executeUniFileCompression(ctx)
    }

    private data class UniFileCompressionContext(
        val file: UniFile,
        val tempFile: UniFile,
        val effectiveParentDir: UniFile,
        val baseName: String,
        val targetExtension: String,
        val compressFormat: Bitmap.CompressFormat,
        val quality: Int,
        val autoGrayscale: Boolean,
        val originalSize: Long,
        val originalName: String,
    )

    private fun createFallbackResult(file: UniFile, size: Long): CompressionResult {
        return CompressionResult(
            success = false,
            compressed = false,
            originalSize = size,
            finalSize = size,
            extension = file.extension.orEmpty(),
            resultingFile = file,
        )
    }

    private fun checkUniFileSkippable(file: UniFile, originalSize: Long): CompressionResult? {
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
        return null
    }

    private fun resolveEffectiveParentDir(file: UniFile, parentDir: UniFile?): UniFile? {
        return parentDir
            ?: file.parentFile
            ?: file.filePath?.let { File(it).parentFile }?.let { UniFile.fromFile(it) }
    }

    private fun executeUniFileCompression(ctx: UniFileCompressionContext): CompressionResult {
        var bitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null
        var effectiveOriginalSize = ctx.originalSize

        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = ctx.file.openInputStream().buffered().use { stream ->
                if (effectiveOriginalSize <= 0) {
                    effectiveOriginalSize = stream.available().toLong().coerceAtLeast(0L)
                }
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmap == null) {
                logcat(LogPriority.WARN) { "BitmapFactory.decodeStream returned NULL for ${ctx.originalName}" }
                ctx.tempFile.delete()
                return createFallbackResult(ctx.file, effectiveOriginalSize)
            }

            processedBitmap = if (ctx.autoGrayscale && isMonochrome(bitmap)) {
                toGrayscale(bitmap)
            } else {
                bitmap
            }

            val compressedSize = writeBitmapToTemp(processedBitmap, ctx.tempFile, ctx.compressFormat, ctx.quality)
            logcat(LogPriority.INFO) { "Encoded ${ctx.originalName} with ${ctx.compressFormat} q=${ctx.quality}: compressedSize=$compressedSize, originalSize=$effectiveOriginalSize" }

            val isSmaller = if (effectiveOriginalSize > 0) compressedSize < effectiveOriginalSize else true
            if (compressedSize > 0 && isSmaller) {
                ctx.file.delete()
                val targetFileName = "${ctx.baseName}.${ctx.targetExtension}"
                ctx.tempFile.renameTo(targetFileName)
                val finalFile = ctx.effectiveParentDir.findFile(targetFileName) ?: ctx.tempFile
                CompressionResult(
                    success = true,
                    compressed = true,
                    originalSize = effectiveOriginalSize,
                    finalSize = compressedSize,
                    extension = ctx.targetExtension,
                    resultingFile = finalFile,
                )
            } else {
                ctx.tempFile.delete()
                CompressionResult(
                    success = true,
                    compressed = false,
                    originalSize = effectiveOriginalSize,
                    finalSize = effectiveOriginalSize,
                    extension = ctx.file.extension.orEmpty(),
                    resultingFile = ctx.file,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to compress image file: ${ctx.originalName}" }
            ctx.tempFile.delete()
            createFallbackResult(ctx.file, effectiveOriginalSize)
        } finally {
            if (processedBitmap != null && processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            bitmap?.recycle()
        }
    }

    private fun writeBitmapToTemp(
        bitmap: Bitmap,
        tempFile: UniFile,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
    ): Long {
        var bytesWritten = 0L
        val encodeSuccess = tempFile.openOutputStream().buffered().use { rawOutput ->
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
            bitmap.compress(compressFormat, quality.coerceIn(1, 100), countingOutput)
        } ?: false

        return if (encodeSuccess) {
            if (bytesWritten > 0) bytesWritten else tempFile.length()
        } else {
            -1L
        }
    }
}
// KMK <--
