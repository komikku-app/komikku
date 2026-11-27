package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import exh.log.xLogD
import exh.log.xLogW
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/**
 * Wrapper per l'inferenza TFLite di Real-ESRGAN, con tiling per gestire
 * pagine manga più grandi della dimensione di input fissa del modello.
 */
class AiUpscaler(
    private val context: Application,
    private val model: UpscaleModel,
    requestedBatchSize: Int,
    requestedOverlap: Int,
) {
    private val variant = model.variantFor(requestedBatchSize)
    private val batchSize = variant.batchSize
    private enum class DelegateMode { GPU, CPU }
    private enum class TensorLayout { NHWC, NCHW }
    private val outSize get() = model.outSize
    private val paddedTileSize get() = model.paddedTileSize

    /*
    Constraints: Odd overlap is truncated to even (margin = overlap/2);
    overlap too large compared to tileContentSize would cause 'step' to collapse to zero or negative in collectTilePositions(),
    causing a loop that never advances. We avoid it by keeping it under the middle of the tile.
     */
    private val overlap = requestedOverlap.coerceIn(0, model.tileContentSize / 2 - 1).let { it - (it % 2) }

    // Buffers sized for the whole batch, not per single tile
    private val batchInputBuffer by lazy {
        ByteBuffer.allocateDirect(batchSize * 4 * paddedTileSize * paddedTileSize * 3).order(ByteOrder.nativeOrder())
    }
    private val batchOutputBuffer = ByteBuffer.allocateDirect(batchSize * 4 * outSize * outSize * 3).order(ByteOrder.nativeOrder())
    private val inputTiles by lazy {
        Array(batchSize) { Bitmap.createBitmap(paddedTileSize, paddedTileSize, Bitmap.Config.ARGB_8888) }
    }
    private val inputCanvases by lazy { inputTiles.map { Canvas(it) } }
    private val reusableOutputTiles by lazy {
        Array(batchSize) { Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888) }
    }
    private data class TilePos(val x: Int, val y: Int)
    private lateinit var inputLayout: TensorLayout
    private lateinit var outputLayout: TensorLayout
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Single dedicated thread: interpreter always created and invoked here
    private val inferenceExecutor = Executors.newSingleThreadExecutor { r ->
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }.apply { name = "AiUpscaler-Inference" }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()
    private val interpreterLazy = lazy(LazyThreadSafetyMode.NONE) {
        createInterpreter(DelegateMode.GPU) ?: createInterpreter(DelegateMode.CPU)!!
    }
    private val interpreter by interpreterLazy
    private fun detectLayout(shape: IntArray): TensorLayout {
        // Channel with value 3 is at index 1 for NCHW, at index 3 for NHWC
        return if (shape[1] == 3) TensorLayout.NCHW else TensorLayout.NHWC
    }
    private fun detectAndCacheLayouts(interpreter: Interpreter) {
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        inputLayout = detectLayout(inTensor.shape())
        outputLayout = detectLayout(outTensor.shape())

        xLogD("Input: layout=$inputLayout")
        xLogD("Output: layout=$outputLayout")
    }
    private val compatList = CompatibilityList()
    private fun createInterpreter(mode: DelegateMode): Interpreter? {
        /*
        For the GPU, let's first check the official compatibility list:
        if the device is not in the list, we don't even try, saving
        the cost of an attempt that we already know would fail or go wrong.
         */
        if (mode == DelegateMode.GPU && !compatList.isDelegateSupportedOnThisDevice) {
            return null
        }

        return try {
            val options = Interpreter.Options()
            when (mode) {
                DelegateMode.GPU -> {
                    val gpuOptions = compatList.bestOptionsForThisDevice
                    options.addDelegate(GpuDelegate(gpuOptions))
                }
                DelegateMode.CPU -> options.setNumThreads(4)
            }

            val newInterpreter = Interpreter(loadModelFile(), options)
            detectAndCacheLayouts(newInterpreter)
            xLogD("Interpreter created with mode=$mode, batch=$batchSize, Shape: ${newInterpreter.getInputTensor(0).shape()}")

            newInterpreter
        } catch (e: Throwable) {
            xLogW("GPU interpret creation failed", e)
            if (mode == DelegateMode.CPU) throw e else null
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val file = File(context.filesDir, "models/${variant.assetFileName}")
        FileInputStream(file).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }

    private fun collectTilePositions(input: Bitmap, step: Int): List<TilePos> {
        val positions = mutableListOf<TilePos>()
        val contentSize = model.tileContentSize
        var y = 0
        while (true) {
            val actualY = if (y + contentSize > input.height) maxOf(0, input.height - contentSize) else y
            var x = 0
            while (true) {
                val actualX = if (x + contentSize > input.width) maxOf(0, input.width - contentSize) else x
                positions.add(TilePos(actualX, actualY))
                if (actualX + contentSize >= input.width) break
                x += step
            }
            if (actualY + contentSize >= input.height) break
            y += step
        }
        return positions
    }

    /**
     * Extracts a tile of size 'paddedTileSize' into 'canvas', with
     * 'contentX'/'contentY' as the corner of the actual content (not the padding).
     * If 'model.paddingPerSide == 0' (Real-ESRGAN), behavior unchanged
     * compared to before. If >0 (waifu2x), the margin around the
     * content is taken from real pixels on the page when available;
     * at the true edges of the page, where there is no other content, the bordering
     * pixel is replicated (CLAMP) instead of leaving empty area or reading
     * out of bitmap bounds. Necessary because those pixels are used
     * by the network as a real context for its receptive field before discarding them.
     */
    private fun drawPaddedTile(source: Bitmap, canvas: Canvas, contentX: Int, contentY: Int) {
        val padding = model.paddingPerSide
        val contentSize = model.tileContentSize

        if (padding == 0) {
            canvas.drawBitmap(
                source,
                Rect(contentX, contentY, contentX + contentSize, contentY + contentSize),
                Rect(0, 0, contentSize, contentSize),
                null,
            )
            return
        }

        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(
            Matrix().apply {
                setTranslate(-(contentX - padding).toFloat(), -(contentY - padding).toFloat())
            },
        )
        tilePaint.shader = shader
        canvas.drawRect(0f, 0f, paddedTileSize.toFloat(), paddedTileSize.toFloat(), tilePaint)
        tilePaint.shader = null
    }

    suspend fun upscale(input: Bitmap): Bitmap {
        val scale = model.scale
        val contentSize = model.tileContentSize

        val outW = input.width * scale
        val outH = input.height * scale
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val margin = overlap / 2
        val step = contentSize - (margin * 2)
        val positions = collectTilePositions(input, step)

        positions.chunked(batchSize).forEach { batch ->
            val realCount = batch.size

            // Reuse input Bitmaps and Canvases to extract tiles
            for (i in 0 until batchSize) {
                val pos = if (i < realCount) batch[i] else batch.last() // Padding duplicando l'ultimo se necessario
                drawPaddedTile(input, inputCanvases[i], pos.x, pos.y)
            }

            // Inference
            val results = withContext(inferenceDispatcher) { runBatchInference(inputTiles) }

            // Draw only the real results onto the final canvas
            for (i in 0 until realCount) {
                val pos = batch[i]
                val cropLeft = if (pos.x == 0) 0 else margin * scale
                val cropTop = if (pos.y == 0) 0 else margin * scale
                val cropRight = if (pos.x + contentSize >= input.width) contentSize * scale else (contentSize - margin) * scale
                val cropBottom = if (pos.y + contentSize >= input.height) contentSize * scale else (contentSize - margin) * scale

                val srcRect = Rect(cropLeft, cropTop, cropRight, cropBottom)
                val destLeft = (pos.x * scale) + cropLeft
                val destTop = (pos.y * scale) + cropTop
                val destRight = (pos.x * scale) + cropRight
                val destBottom = (pos.y * scale) + cropBottom

                canvas.drawBitmap(results[i], srcRect, Rect(destLeft, destTop, destRight, destBottom), null)
            }
        }
        return output
    }

    private fun runBatchInference(tiles: Array<Bitmap>): Array<Bitmap> {
        val activeInterpreter = interpreter

        val t0 = System.currentTimeMillis()

        // Native C++ write into buffers
        batchInputBuffer.clear()
        val tilePixelCount = paddedTileSize * paddedTileSize
        for (i in tiles.indices) {
            val pixelOffset = i * tilePixelCount
            if (inputLayout == TensorLayout.NHWC) {
                NativePixelOps.writeBitmapToBufferNHWC(tiles[i], batchInputBuffer, pixelOffset, paddedTileSize)
            } else {
                NativePixelOps.writeBitmapToBufferNCHW(tiles[i], batchInputBuffer, pixelOffset, paddedTileSize)
            }
        }
        batchInputBuffer.rewind()
        val t1 = System.currentTimeMillis()

        // TFLite running
        batchOutputBuffer.clear()
        activeInterpreter.run(batchInputBuffer, batchOutputBuffer)
        val t2 = System.currentTimeMillis()

        // Native C++ read from buffer to reusable Bitmaps
        batchOutputBuffer.rewind()
        val outTilePixelCount = outSize * outSize
        for (i in tiles.indices) {
            val pixelOffset = i * outTilePixelCount
            if (outputLayout == TensorLayout.NHWC) {
                NativePixelOps.readBufferToBitmapNHWC(batchOutputBuffer, pixelOffset, reusableOutputTiles[i], outSize)
            } else {
                NativePixelOps.readBufferToBitmapNCHW(batchOutputBuffer, pixelOffset, reusableOutputTiles[i], outSize)
            }
        }
        val t3 = System.currentTimeMillis()

        xLogD("Native write: ${t1 - t0}ms | TFLite run(): ${t2 - t1}ms | Native read: ${t3 - t2}ms | Total: ${t3 - t0}ms")

        return reusableOutputTiles
    }

    fun close() {
        if (interpreterLazy.isInitialized()) interpreter.close()
        inferenceExecutor.shutdown()
    }
}
