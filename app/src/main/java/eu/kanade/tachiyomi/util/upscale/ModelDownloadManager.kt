package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.TimeUnit

sealed class ModelDownloadState {
    data object NotDownloaded : ModelDownloadState()
    data object Queued : ModelDownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data object Downloaded : ModelDownloadState()
    data class Failed(val reason: String?) : ModelDownloadState()
}

fun ModelDownloadState.toChapterDownloadState(): Download.State = when (this) {
    ModelDownloadState.NotDownloaded -> Download.State.NOT_DOWNLOADED
    ModelDownloadState.Queued -> Download.State.QUEUE
    is ModelDownloadState.Downloading -> Download.State.DOWNLOADING
    ModelDownloadState.Downloaded -> Download.State.DOWNLOADED
    is ModelDownloadState.Failed -> Download.State.ERROR
}

fun ModelDownloadState.progressPercent(): Int = when (this) {
    is ModelDownloadState.Downloading -> if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
    else -> 0
}

class ModelDownloadManager(private val context: Application) {

    private val workManager = WorkManager.getInstance(context)
    private val localChangeSignal = MutableStateFlow(0)

    private fun uniqueWorkName(model: UpscaleModel, batchSize: Int) = "download_model_${model.name}_B$batchSize"

    fun isDownloaded(model: UpscaleModel, batchSize: Int): Boolean {
        val entry = ModelManifestLoader.entryFor(context, model, batchSize) ?: return false
        return File(context.filesDir, "models/${entry.assetFileName}").exists()
    }

    fun enqueueDownload(model: UpscaleModel, batchSize: Int, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to model.name,
                    ModelDownloadWorker.KEY_BATCH_SIZE to batchSize,
                ),
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // If the user re-starts the download process of one already in progress we don't duplicate the worker
        workManager.enqueueUniqueWork(uniqueWorkName(model, batchSize), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload(model: UpscaleModel, batchSize: Int) {
        workManager.cancelUniqueWork(uniqueWorkName(model, batchSize))
    }

    fun deleteDownloaded(model: UpscaleModel, batchSize: Int) {
        val entry = ModelManifestLoader.entryFor(context, model, batchSize) ?: return
        File(context.filesDir, "models/${entry.assetFileName}").delete()
        localChangeSignal.update { it + 1 } // restarts observeState even if WorkManager is not involved
    }

    fun observeState(model: UpscaleModel, batchSize: Int): Flow<ModelDownloadState> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(model, batchSize)),
            localChangeSignal,
        ) { infos, _ -> infos }
            .map { infos ->
                val info = infos.firstOrNull()
                when {
                    isDownloaded(model, batchSize) -> ModelDownloadState.Downloaded
                    info == null -> ModelDownloadState.NotDownloaded
                    info.state == WorkInfo.State.RUNNING -> ModelDownloadState.Downloading(
                        bytesDownloaded = info.progress.getLong(ModelDownloadWorker.KEY_PROGRESS_BYTES, 0L),
                        totalBytes = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L),
                    )
                    info.state == WorkInfo.State.FAILED -> ModelDownloadState.Failed(
                        info.outputData.getString(ModelDownloadWorker.KEY_ERROR),
                    )
                    info.state == WorkInfo.State.ENQUEUED -> ModelDownloadState.Queued
                    else -> ModelDownloadState.NotDownloaded
                }
            }
}
