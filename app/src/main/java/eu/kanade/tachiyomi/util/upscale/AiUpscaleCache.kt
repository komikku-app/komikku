package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import com.jakewharton.disklrucache.DiskLruCache
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.storage.DiskUtil
import exh.log.xLogW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.BufferedSource
import okio.buffer
import okio.source
import tachiyomi.decoder.ImageDecoder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Disk cache for already upscaled pages
 */
object AiUpscaleCache {

    private val context: Application by lazy { Injekt.get() }
    private val readerPreferences: ReaderPreferences by lazy { Injekt.get() }
    private var currentUpscaler: AiUpscaler? = null
    private var currentModel: UpscaleModel? = null
    private var currentBatch: Int? = null
    private var currentOverlap: Int? = null

    private val cacheDir: File by lazy {
        File(context.cacheDir, "ai_upscale_cache").apply { mkdirs() }
    }
    private var diskCache: DiskLruCache = setupDiskCache(readerPreferences.aiUpscaleCacheSize().get())
    private val modelDownloadManager: ModelDownloadManager by lazy { Injekt.get() }

    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private const val UPSCALE_CACHE_QUALITY = 95

    init {
        readerPreferences.aiUpscaleCacheSize().changes()
            .drop(1)
            .onEach {
                val old = diskCache
                diskCache = setupDiskCache(it)
                old.close()
            }
            .launchIn(CoroutineScope(Job() + Dispatchers.Main))
    }

    private fun setupDiskCache(cacheSizeMb: Int): DiskLruCache = DiskLruCache.open(
        File(context.cacheDir, "ai_upscale_cache"),
        1, // app version
        1, // value count per entry
        cacheSizeMb.toLong() * 1024 * 1024,
    )

    private fun getUpscaler(model: UpscaleModel, batch: Int, overlap: Int): AiUpscaler? {
        BundledModelInstaller.ensureInstalled(context, UpscaleModel.REALESRGAN_ANIMEVIDEOV3, batchSize = 1)

        if (!modelDownloadManager.isDownloaded(model, batch)) {
            modelDownloadManager.enqueueDownload(model, batch, wifiOnly = readerPreferences.aiUpscaleWifiOnlyDownloads().get())
            xLogW("Model ${model.name} B$batch not ready, download started, upscaling skipped for this page")
            return null
        }

        if (model != currentModel || batch != currentBatch || overlap != currentOverlap) {
            currentUpscaler?.close()
            currentUpscaler = AiUpscaler(context, model, batch, overlap)
            currentModel = model
            currentBatch = batch
            currentOverlap = overlap
        }
        return currentUpscaler!!
    }
    suspend fun getOrUpscale(
        chapterId: Long?,
        pageIndex: Int,
        source: BufferedSource,
        targetWidth: Int,
        priority: UpscalePriorityGate.Priority = UpscalePriorityGate.Priority.VISIBLE,
    ): BufferedSource? {
        val model = readerPreferences.aiUpscaleModel().get()
        val batch = readerPreferences.aiUpscaleBatchSize().get()
        val overlap = readerPreferences.aiUpscaleTileOverlap().get()
        val configTag = "${model.name}_B${batch}_O$overlap"
        val key = DiskUtil.hashKeyForDisk("${chapterId}_${pageIndex}_$configTag")

        readFromCache(key)?.let { return it }

        return UpscalePriorityGate.withPermit(priority) {
            readFromCache(key)?.let { return@withPermit it }

            val decoded = try {
                ImageDecoder.newInstance(source.inputStream())?.decode()
            } catch (e: Exception) {
                null
            } ?: return@withPermit null

            val resized = if (decoded.width > targetWidth) {
                val scale = targetWidth.toFloat() / decoded.width
                val newHeight = (decoded.height * scale).toInt()
                Bitmap.createScaledBitmap(decoded, targetWidth, newHeight, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val upscaled = try {
                getUpscaler(model, batch, overlap)?.upscale(resized)
            } catch (e: OutOfMemoryError) {
                resized
            }
            if (resized !== decoded && resized !== upscaled) resized.recycle()

            writeToCache(key, upscaled).also {
                if (upscaled !== decoded && upscaled !== resized) upscaled?.recycle()
            }
        }
    }

    private fun readFromCache(key: String): BufferedSource? = try {
        diskCache.get(key)?.getInputStream(0)?.source()?.buffer()
    } catch (e: Exception) {
        null
    }

    private fun writeToCache(key: String, bitmap: Bitmap?): BufferedSource? {
        if (bitmap == null) return null
        var editor: DiskLruCache.Editor? = null
        return try {
            editor = diskCache.edit(key) ?: return null
            editor.newOutputStream(0).use { out ->
                bitmap.compress(webpCompressFormat(), UPSCALE_CACHE_QUALITY, out)
            }
            diskCache.flush()
            editor.commit()
            readFromCache(key)
        } catch (e: Exception) {
            editor?.abortUnlessCommitted()
            null
        }
    }

    fun clear(): Int {
        var count = 0
        diskCache.directory.listFiles()?.forEach {
            if (it.name != "journal" && !it.name.startsWith("journal.")) count++
        }
        diskCache.delete()
        diskCache = setupDiskCache(readerPreferences.aiUpscaleCacheSize().get())
        return count
    }
}
