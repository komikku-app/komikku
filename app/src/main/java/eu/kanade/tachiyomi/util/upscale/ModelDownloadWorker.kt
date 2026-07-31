package eu.kanade.tachiyomi.util.upscale

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_BATCH_SIZE = "batch_size"
        const val KEY_PROGRESS_BYTES = "progress_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
    }

    private val httpClient = OkHttpClient.Builder().build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: run {
            Log.e("ModelDownloadWorker", "model_id mancante in inputData")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "model_id mancante"))
        }
        val batchSize = inputData.getInt(KEY_BATCH_SIZE, -1)
        if (batchSize <= 0) {
            Log.e("ModelDownloadWorker", "batch_size mancante o invalido: $batchSize")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "batch_size mancante"))
        }

        val model = UpscaleModel.entries.find { it.name == modelId } ?: run {
            Log.e("ModelDownloadWorker", "modello sconosciuto: $modelId")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "modello sconosciuto: $modelId"))
        }
        val entry = ModelManifestLoader.entryFor(applicationContext, model, batchSize) ?: run {
            Log.e("ModelDownloadWorker", "nessuna entry manifest per $modelId/B$batchSize — controlla models_manifest.json")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "nessuna entry manifest per $modelId/B$batchSize"))
        }

        Log.d("ModelDownloadWorker", "Scarico ${entry.assetFileName} da ${entry.downloadUrl}")

        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val destFile = File(modelsDir, entry.assetFileName)
        val tmpFile = File(modelsDir, "${entry.assetFileName}.part")

        try {
            downloadWithResume(entry, tmpFile)

            val actualSha256 = sha256Of(tmpFile)
            if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
                Log.e("ModelDownloadWorker", "Checksum non corrispondente per ${entry.assetFileName}: atteso=${entry.sha256} ottenuto=$actualSha256")
                tmpFile.delete()
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "checksum non corrispondente (atteso ${entry.sha256}, ottenuto $actualSha256)"),
                )
            }

            tmpFile.renameTo(destFile)
            Log.d("ModelDownloadWorker", "${entry.assetFileName} scaricato e verificato con successo")
            Result.success()
        } catch (e: Exception) {
            if (isStopped) {
                Log.w("ModelDownloadWorker", "Download annullato per ${entry.assetFileName}")
                Result.failure(workDataOf(KEY_ERROR to "annullato"))
            } else {
                Log.e("ModelDownloadWorker", "Errore scaricando ${entry.assetFileName}, riprovo", e)
                Result.retry()
            }
        }
    }

    private suspend fun downloadWithResume(entry: ModelVariantEntry, tmpFile: File) {
        val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L

        val requestBuilder = Request.Builder().url(entry.downloadUrl)
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} scaricando ${entry.assetFileName}")

            // Se il server ignora il Range (torna 200 invece di 206), ripartiamo da zero
            // per evitare di appendere dati sbagliati in coda a un file incompleto.
            val resumed = response.code == 206 && existingBytes > 0
            val body = response.body ?: throw java.io.IOException("Body vuoto")

            var bytesWritten = if (resumed) existingBytes else 0L
            java.io.FileOutputStream(tmpFile, resumed).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw java.io.InterruptedIOException("Worker fermato")
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesWritten += read
                        setProgressAsync(
                            workDataOf(
                                KEY_PROGRESS_BYTES to bytesWritten,
                                KEY_TOTAL_BYTES to entry.sizeBytes,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
