package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import okio.BufferedSource
import okio.buffer
import okio.source
import tachiyomi.decoder.ImageDecoder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Cache su disco per le pagine già upscalate, chiave = capitolo + indice pagina.
 * Necessaria perché il reader disabilita esplicitamente la cache di Coil per le pagine
 * (vedi ReaderPageImageView), quindi senza questo layer ogni ri-visualizzazione
 * di una pagina già letta rilancerebbe l'inferenza da zero.
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
    private val modelDownloadManager: ModelDownloadManager by lazy { Injekt.get() }

    private fun getUpscaler(): AiUpscaler? {
        val model = readerPreferences.aiUpscaleModel().get()
        val batch = readerPreferences.aiUpscaleBatchSize().get()
        val overlap = readerPreferences.aiUpscaleTileOverlap().get()

        BundledModelInstaller.ensureInstalled(context, UpscaleModel.REALESRGAN_ANIMEVIDEOV3, batchSize = 1)

        if (!modelDownloadManager.isDownloaded(model, batch)) {
            // Non blocchiamo la lettura: avviamo il download in background (se non già in corso,
            // enqueueUniqueWork con KEEP evita duplicati) e per questa pagina saltiamo l'upscaling.
            modelDownloadManager.enqueueDownload(model, batch, wifiOnly = readerPreferences.aiUpscaleWifiOnlyDownloads().get())
            Log.w("AiUpscaleCache", "Modello ${model.name} B$batch non pronto, download avviato, upscaling saltato per questa pagina")
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
        val configTag = "${currentModel?.name}_B${currentBatch}_O${currentOverlap}"
        val file = File(cacheDir, "${chapterId}_${pageIndex}_$configTag.jpg")
        if (file.exists()) {
            return file.source().buffer()
        }

        return UpscalePriorityGate.withPermit(priority) {
            // Nel frattempo, mentre eravamo in coda, un'altra richiesta per
            // la STESSA pagina potrebbe averla già completata.
            if (file.exists()) return@withPermit file.source().buffer()

            val decoded = try {
                ImageDecoder.newInstance(source.inputStream())?.decode()
            } catch (e: Exception) { null } ?: return@withPermit null

            val resized = if (decoded.width > targetWidth) {
                val scale = targetWidth.toFloat() / decoded.width
                val newHeight = (decoded.height * scale).toInt()
                Bitmap.createScaledBitmap(decoded, targetWidth, newHeight, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            Log.d("AiUpscaleCache", "Upscaling chapterId: $chapterId, pageIndex: $pageIndex")

            val upscaled = try {
                getUpscaler()?.upscale(resized)
            } catch (e: OutOfMemoryError) {
                resized
            }
            if (resized !== decoded && resized !== upscaled) resized.recycle()

            Log.d("AiUpscaleCache", "Upscaling done for chapterId: ${chapterId}, pageIndex: ${pageIndex}")

            try {
                file.outputStream().use { out ->
                    upscaled?.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (upscaled !== decoded && upscaled !== resized) upscaled?.recycle()
                file.source().buffer()
            } catch (e: Exception) {
                null
            }
        }
    }
}
