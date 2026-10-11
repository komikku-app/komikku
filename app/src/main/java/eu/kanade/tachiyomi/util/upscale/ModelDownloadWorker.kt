package eu.kanade.tachiyomi.util.upscale

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import exh.log.xLogD
import exh.log.xLogE
import exh.log.xLogW
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
            xLogE("Missing model_id in inputData")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "missing model_id"))
        }
        val batchSize = inputData.getInt(KEY_BATCH_SIZE, -1)
        if (batchSize <= 0) {
            xLogE("batch_size missing or invalid: $batchSize")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "batch_size missing"))
        }

        val model = UpscaleModel.entries.find { it.name == modelId } ?: run {
            xLogE("Unknown model: $modelId")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "unknown model: $modelId"))
        }
        val entry = ModelManifestLoader.entryFor(applicationContext, model, batchSize) ?: run {
            xLogE("No manifest entry for $modelId/B$batchSize — check models_manifest.json")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "no manifest entry for $modelId/B$batchSize"))
        }

        xLogD("Downloading ${entry.assetFileName} from ${entry.downloadUrl}")

        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val destFile = File(modelsDir, entry.assetFileName)
        val tmpFile = File(modelsDir, "${entry.assetFileName}.part")

        try {
            downloadWithResume(entry, tmpFile)

            val actualSha256 = sha256Of(tmpFile)
            if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
                xLogE("Checksum not matching for ${entry.assetFileName}: expected=${entry.sha256} received=$actualSha256")
                tmpFile.delete()
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "checksum not matching (expected ${entry.sha256}, received $actualSha256)"),
                )
            }

            tmpFile.renameTo(destFile)
            xLogD("${entry.assetFileName} downloaded and verified successfully")
            Result.success()
        } catch (e: Exception) {
            if (isStopped) {
                xLogW("Download cancelled for ${entry.assetFileName}")
                Result.failure(workDataOf(KEY_ERROR to "cancelled"))
            } else {
                xLogE("Error downloading ${entry.assetFileName}, retrying", e)
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
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} downloading ${entry.assetFileName}")

            // If server ignore Range (returns 200 instead of 206), start again from zero
            val resumed = response.code == 206 && existingBytes > 0
            val body = response.body

            var bytesWritten = if (resumed) existingBytes else 0L
            java.io.FileOutputStream(tmpFile, resumed).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw java.io.InterruptedIOException("Worker stopped")
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
