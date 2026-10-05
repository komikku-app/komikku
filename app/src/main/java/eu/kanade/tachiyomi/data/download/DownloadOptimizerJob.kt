// KMK -->
package eu.kanade.tachiyomi.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.archive.ArchiveInputStream
import mihon.core.archive.CbzCrypto
import mihon.core.archive.ZipWriter
import mihon.core.archive.archiveReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageCompressor
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val UNCOMPRESSED_EXTS = setOf("jpg", "jpeg", "png", "bmp")
private val COMPRESSED_EXTS = setOf("webp", "avif")

private class ImageCounts(var uncompressed: Int = 0, var compressed: Int = 0) {
    fun record(ext: String) {
        if (ext in UNCOMPRESSED_EXTS) {
            uncompressed++
        } else if (ext in COMPRESSED_EXTS) {
            compressed++
        }
    }
    val isEligible: Boolean get() {
        val total = uncompressed + compressed
        return total > 0 && uncompressed.toDouble() / total > 0.10
    }
}

private data class CompressionConfig(
    val format: String,
    val quality: Int,
    val autoGrayscale: Boolean,
    val stripMetadata: Boolean,
)

private data class ChapterProgress(
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val seriesTitle: String? = null,
    val chapterTitle: String = "",
)

class DownloadOptimizerJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val storageManager: StorageManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    private var lastNotificationTime = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        return ForegroundInfo(
            Notifications.ID_STORAGE_OPTIMIZER_PROGRESS,
            notification,
            foregroundServiceType,
        )
    }

    private fun updateProgressNotification(
        progress: ChapterProgress = ChapterProgress(),
        isExtracting: Boolean = false,
        currentPage: Int = 0,
        totalPages: Int = 0,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationTime < 350L) {
            return
        }
        lastNotificationTime = now

        try {
            val notification = createNotification(
                progress = progress,
                isExtracting = isExtracting,
                currentPage = currentPage,
                totalPages = totalPages,
            )
            context.notificationManager.notify(
                Notifications.ID_STORAGE_OPTIMIZER_PROGRESS,
                notification,
            )
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to update notification progress" }
        }
    }

    private fun createNotification(
        progress: ChapterProgress = ChapterProgress(),
        isExtracting: Boolean = false,
        currentPage: Int = 0,
        totalPages: Int = 0,
    ): Notification {
        val cancelIntent = NotificationReceiver.cancelDownloadOptimizerPendingBroadcast(context)
        val title = if (progress.totalChapters > 0) {
            context.stringResource(KMR.strings.optimize_notification_title, progress.chapterIndex, progress.totalChapters)
        } else {
            context.stringResource(KMR.strings.optimize_notification_channel)
        }

        val baseText = if (!progress.seriesTitle.isNullOrBlank() && !progress.chapterTitle.isNullOrBlank()) {
            "${progress.seriesTitle} • ${progress.chapterTitle}"
        } else if (!progress.chapterTitle.isNullOrBlank()) {
            progress.chapterTitle
        } else if (progress.totalChapters > 0) {
            context.stringResource(KMR.strings.optimize_notification_running, progress.chapterIndex, progress.totalChapters)
        } else {
            context.stringResource(KMR.strings.optimize_calculating_chapters)
        }

        val contentText = if (isExtracting) {
            "$baseText (${context.stringResource(KMR.strings.optimize_notification_extracting)})"
        } else {
            baseText
        }

        return context.notificationBuilder(Notifications.CHANNEL_STORAGE_OPTIMIZER_PROGRESS) {
            setContentTitle(title)
            setContentText(contentText)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
            setOngoing(true)
            if (isExtracting || (totalPages <= 0 && progress.totalChapters <= 0)) {
                setProgress(0, 0, true)
            } else if (totalPages > 0) {
                setProgress(totalPages, currentPage, false)
            } else {
                setProgress(progress.totalChapters, progress.chapterIndex, false)
            }
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.stringResource(KMR.strings.optimize_notification_cancel),
                cancelIntent,
            )
        }.build()
    }

    override suspend fun doWork(): Result {
        setForegroundSafely()

        val downloadsDir = storageManager.getDownloadsDirectory() ?: return Result.failure()
        val format = inputData.getString(KEY_FORMAT) ?: downloadPreferences.downloadCompressionFormat().get()
        val quality = inputData.getInt(KEY_QUALITY, -1).takeIf { it != -1 } ?: downloadPreferences.downloadCompressionQuality().get()
        val autoGrayscale = if (inputData.keyValueMap.containsKey(KEY_AUTO_GRAYSCALE)) inputData.getBoolean(KEY_AUTO_GRAYSCALE, true) else downloadPreferences.autoGrayscaleBWManga().get()
        val stripMetadata = if (inputData.keyValueMap.containsKey(KEY_STRIP_METADATA)) inputData.getBoolean(KEY_STRIP_METADATA, true) else downloadPreferences.stripImageMetadata().get()

        val allChapters = getEligibleChapters(context, downloadsDir)
        val selectedUris = inputData.getStringArray(KEY_SELECTED_CHAPTERS)?.toSet()
        val chapters = filterChapters(allChapters, selectedUris)

        logcat(LogPriority.INFO) { "DownloadOptimizerJob: found ${chapters.size} eligible chapters to optimize (out of ${allChapters.size} total)" }
        if (chapters.isEmpty()) return Result.success()

        var totalSavedBytes = 0L
        try {
            val config = CompressionConfig(format, quality, autoGrayscale, stripMetadata)
            totalSavedBytes = processChapters(chapters, config)
        } finally {
            context.cancelNotification(Notifications.ID_STORAGE_OPTIMIZER_PROGRESS)
        }

        notifyComplete(totalSavedBytes)
        DownloadOptimizerState.clearCache()
        return Result.success()
    }

    private fun filterChapters(allChapters: List<UniFile>, selectedUris: Set<String>?): List<UniFile> {
        if (selectedUris == null) return allChapters
        if (selectedUris.isEmpty()) return emptyList()

        val decodedSelected = selectedUris.map { Uri.decode(it) }.toSet()
        return allChapters.filter { chapter ->
            isChapterSelected(chapter, selectedUris, decodedSelected)
        }
    }

    private fun isChapterSelected(
        chapter: UniFile,
        selectedUris: Set<String>,
        decodedSelected: Set<String>,
    ): Boolean {
        val uriStr = chapter.uri.toString()
        val decodedUri = Uri.decode(uriStr)
        if (selectedUris.contains(uriStr) || decodedSelected.contains(decodedUri)) return true

        val filePath = chapter.filePath
        if (filePath != null && (selectedUris.contains(filePath) || decodedSelected.contains(filePath))) return true

        val mangaName = chapter.parentFile?.name?.let { Uri.decode(it) }
        val chapterName = chapter.name?.let { Uri.decode(it) }
        if (mangaName != null && chapterName != null) {
            val relativePath = "/$mangaName/$chapterName"
            val encodedRelativePath = "%2F$mangaName%2F$chapterName"
            return decodedSelected.any { it.contains(relativePath) } ||
                selectedUris.any { it.contains(encodedRelativePath) }
        }
        return false
    }

    private fun processChapters(
        chapters: List<UniFile>,
        config: CompressionConfig,
    ): Long {
        var totalSavedBytes = 0L
        for ((index, chapter) in chapters.withIndex()) {
            if (isStopped) {
                notifyCanceled()
                break
            }

            val progress = ChapterProgress(
                chapterIndex = index + 1,
                totalChapters = chapters.size,
                seriesTitle = chapter.parentFile?.name?.takeIf { it != "downloads" },
                chapterTitle = chapter.nameWithoutExtension ?: chapter.name.orEmpty(),
            )

            try {
                val saved = if (chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)) {
                    optimizeCbzChapter(chapter, config, progress)
                } else if (chapter.isDirectory) {
                    optimizeDirectoryChapter(chapter, config, progress)
                } else {
                    0L
                }
                if (saved > 0) totalSavedBytes += saved
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to optimize chapter: ${chapter.name}" }
            }
        }
        return totalSavedBytes
    }

    private fun optimizeDirectoryChapter(
        chapterDir: UniFile,
        config: CompressionConfig,
        progress: ChapterProgress,
    ): Long {
        var savedBytes = 0L
        var hadError = false
        val files = chapterDir.listFiles().orEmpty().filter {
            !it.name.orEmpty().endsWith(".tmp") && !it.name.orEmpty().endsWith(".tmp_comp") && ImageUtil.isImage(it.name)
        }

        val totalImages = files.size
        var currentImage = 0

        updateProgressNotification(
            progress = progress,
            isExtracting = false,
            currentPage = 0,
            totalPages = totalImages,
            force = true,
        )

        for (file in files) {
            if (isStopped) break
            if (!ImageCompressor.isAlreadyCompressed(file)) {
                val result = ImageCompressor.compressFile(
                    file = file,
                    format = config.format,
                    quality = config.quality,
                    autoGrayscale = config.autoGrayscale,
                    stripMetadata = config.stripMetadata,
                )
                if (!result.success) {
                    hadError = true
                }
                if (result.compressed) {
                    savedBytes += result.originalSize - result.finalSize
                }
            }
            currentImage++
            updateProgressNotification(
                progress = progress,
                isExtracting = false,
                currentPage = currentImage,
                totalPages = totalImages,
                force = currentImage == 1 || currentImage == totalImages,
            )
        }
        if (!isStopped && !hadError) {
            chapterDir.createFile(OPTIMIZED_MARKER)
        }
        return savedBytes
    }

    private fun optimizeCbzChapter(
        cbzFile: UniFile,
        config: CompressionConfig,
        progress: ChapterProgress,
    ): Long {
        if (cbzFile.parentFile == null || cbzFile.name == null) return 0L
        val originalSize = cbzFile.length()
        val tempExtractDir = File(context.cacheDir, "cbz_opt_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        updateProgressNotification(
            progress = progress,
            isExtracting = true,
            force = true,
        )

        val localInputPair = getLocalInputFile(cbzFile)
        val localInputFile = localInputPair.first
        val isTempInputFile = localInputPair.second

        try {
            val isEncrypted = isArchiveEncrypted(cbzFile)
            extractArchive(cbzFile, localInputFile, tempExtractDir, isEncrypted)

            val outcome = compressExtractedImages(
                tempExtractDir = tempExtractDir,
                config = config,
                progress = progress,
            )

            if (isStopped) return 0L

            if (!outcome.hadError) {
                File(tempExtractDir, OPTIMIZED_MARKER).createNewFile()
            }

            return repackAndReplaceCbz(
                cbzFile = cbzFile,
                tempExtractDir = tempExtractDir,
                isEncrypted = isEncrypted,
                originalSize = originalSize,
                anyCompressed = outcome.anyCompressed,
                hadError = outcome.hadError,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to optimize CBZ: ${cbzFile.name}" }
            return 0L
        } finally {
            if (isTempInputFile) {
                localInputFile?.delete()
            }
            tempExtractDir.deleteRecursively()
        }
    }

    private fun getLocalInputFile(cbzFile: UniFile): Pair<File?, Boolean> {
        var localInputFile = cbzFile.toLocalFile()
        var isTempInputFile = false

        if (localInputFile == null) {
            val tempIn = File(context.cacheDir, "input_${System.currentTimeMillis()}.cbz")
            cbzFile.openInputStream()?.buffered()?.use { input ->
                tempIn.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            localInputFile = tempIn
            isTempInputFile = true
        }
        return Pair(localInputFile, isTempInputFile)
    }

    private fun isArchiveEncrypted(cbzFile: UniFile): Boolean {
        return try {
            var encrypted = false
            cbzFile.archiveReader(context).use { reader ->
                encrypted = reader.encrypted
            }
            encrypted
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to check encryption for ${cbzFile.name}" }
            false
        }
    }

    private fun extractArchive(cbzFile: UniFile, localInputFile: File?, tempExtractDir: File, isEncrypted: Boolean) {
        val extractSuccess = if (!isEncrypted && localInputFile != null) {
            extractLocalZip(cbzFile, localInputFile, tempExtractDir)
        } else {
            false
        }

        if (!extractSuccess) {
            extractWithArchiveReader(cbzFile, tempExtractDir)
        }
        logcat(LogPriority.INFO) { "Extracted files for ${cbzFile.name} to $tempExtractDir: ${tempExtractDir.list()?.size} entries" }
    }

    private fun extractLocalZip(cbzFile: UniFile, localInputFile: File, tempExtractDir: File): Boolean {
        return try {
            ZipFile(localInputFile).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    extractZipEntry(zf, entries.nextElement(), tempExtractDir)
                }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "ZipFile extraction failed for ${cbzFile.name}, trying ArchiveReader" }
            false
        }
    }

    private fun extractZipEntry(zf: ZipFile, entry: java.util.zip.ZipEntry, tempExtractDir: File) {
        if (entry.isDirectory) return
        val outFile = File(tempExtractDir, entry.name)
        outFile.parentFile?.mkdirs()
        zf.getInputStream(entry).use { inStream ->
            outFile.outputStream().buffered().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun extractWithArchiveReader(cbzFile: UniFile, tempExtractDir: File) {
        cbzFile.archiveReader(context).use { reader ->
            reader.useEntries { entries ->
                entries.filter { it.isFile }.forEach { extractArchiveReaderEntry(reader, it, tempExtractDir) }
            }
        }
    }

    private fun extractArchiveReaderEntry(reader: mihon.core.archive.ArchiveReader, entry: mihon.core.archive.ArchiveEntry, tempExtractDir: File) {
        val outFile = File(tempExtractDir, entry.name)
        outFile.parentFile?.mkdirs()
        outFile.outputStream().buffered().use { out ->
            reader.getInputStream(entry.name)?.use { input ->
                input.copyTo(out)
            }
        }
    }

    private data class CompressionOutcome(
        val anyCompressed: Boolean,
        val hadError: Boolean,
    )

    private fun compressExtractedImages(
        tempExtractDir: File,
        config: CompressionConfig,
        progress: ChapterProgress,
    ): CompressionOutcome {
        var anyCompressed = false
        var hadError = false
        val imageFiles = tempExtractDir.walkTopDown().filter { file ->
            file.isFile && !file.name.endsWith(".tmp") && !file.name.endsWith(".tmp_comp") && ImageUtil.isImage(file.name) { file.inputStream() }
        }.toList()
        val totalImages = imageFiles.size
        var currentImage = 0

        updateProgressNotification(
            progress = progress,
            isExtracting = false,
            currentPage = 0,
            totalPages = totalImages,
            force = true,
        )

        for (file in imageFiles) {
            if (isStopped) return CompressionOutcome(anyCompressed, hadError)
            if (!ImageCompressor.isAlreadyCompressed(file)) {
                val res = ImageCompressor.compressFile(
                    file = file,
                    format = config.format,
                    quality = config.quality,
                    autoGrayscale = config.autoGrayscale,
                    stripMetadata = config.stripMetadata,
                )
                logcat(LogPriority.INFO) { "Compression result for ${file.name}: success=${res.success}, compressed=${res.compressed}, orig=${res.originalSize}, final=${res.finalSize}" }
                if (!res.success) {
                    hadError = true
                }
                if (res.compressed) {
                    anyCompressed = true
                }
            }
            currentImage++
            updateProgressNotification(
                progress = progress,
                isExtracting = false,
                currentPage = currentImage,
                totalPages = totalImages,
                force = currentImage == 1 || currentImage == totalImages,
            )
        }
        return CompressionOutcome(anyCompressed, hadError)
    }

    private fun repackAndReplaceCbz(
        cbzFile: UniFile,
        tempExtractDir: File,
        isEncrypted: Boolean,
        originalSize: Long,
        anyCompressed: Boolean,
        hadError: Boolean,
    ): Long {
        val parentDir = cbzFile.parentFile ?: return 0L
        val finalName = cbzFile.name ?: return 0L
        val tempCbzName = "$finalName.tmp_opt.cbz"
        val tempCbz = parentDir.createFile(tempCbzName) ?: return 0L

        if (isEncrypted) {
            writeEncryptedArchive(tempCbz, tempExtractDir)
        } else {
            writePlainArchive(tempCbz, tempExtractDir)
        }

        val newSize = tempCbz.length()
        logcat(LogPriority.INFO) { "CBZ optimization for ${cbzFile.name}: originalSize=$originalSize, newSize=$newSize, anyCompressed=$anyCompressed, hadError=$hadError" }
        val shouldReplace = newSize > 0 && (newSize < originalSize || (!hadError && !anyCompressed && newSize <= originalSize + 4096))
        if (shouldReplace) {
            cbzFile.delete()
            tempCbz.renameTo(finalName)
            return if (newSize < originalSize) originalSize - newSize else 0L
        } else {
            tempCbz.delete()
            return 0L
        }
    }

    private fun writeEncryptedArchive(tempCbz: UniFile, tempExtractDir: File) {
        ZipWriter(context, tempCbz, true).use { writer ->
            tempExtractDir.walkTopDown().filter { it.isFile }.forEach { file ->
                UniFile.fromFile(file)?.let { writer.write(it) }
            }
        }
    }

    private fun writePlainArchive(tempCbz: UniFile, tempExtractDir: File) {
        tempCbz.openOutputStream()?.buffered()?.let { outStream ->
            java.util.zip.ZipOutputStream(outStream).use { zipOut ->
                tempExtractDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativePath = file.relativeTo(tempExtractDir).path.replace('\\', '/')
                    zipOut.putNextEntry(java.util.zip.ZipEntry(relativePath))
                    file.inputStream().buffered().use { inStream ->
                        inStream.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun notifyComplete(savedBytes: Long) {
        val readableSaved = Formatter.formatFileSize(context, savedBytes)
        val title = context.stringResource(KMR.strings.optimize_notification_channel)
        val text = context.stringResource(KMR.strings.optimize_notification_complete, readableSaved)

        val notification = context.notificationBuilder(Notifications.CHANNEL_STORAGE_OPTIMIZER_COMPLETE) {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
            setAutoCancel(true)
        }.build()

        context.notificationManager.notify(Notifications.ID_STORAGE_OPTIMIZER_COMPLETE, notification)
    }

    private fun notifyCanceled() {
        val title = context.stringResource(KMR.strings.optimize_notification_channel)
        val text = context.stringResource(KMR.strings.optimize_notification_canceled)

        val notification = context.notificationBuilder(Notifications.CHANNEL_STORAGE_OPTIMIZER_COMPLETE) {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
            setAutoCancel(true)
        }.build()

        context.notificationManager.notify(Notifications.ID_STORAGE_OPTIMIZER_COMPLETE, notification)
    }

    companion object {
        private const val TAG = "DownloadOptimizer"
        const val OPTIMIZED_MARKER = ".optimized"
        const val KEY_FORMAT = "format"
        const val KEY_QUALITY = "quality"
        const val KEY_EFFORT = "effort"
        const val KEY_AUTO_GRAYSCALE = "auto_grayscale"
        const val KEY_STRIP_METADATA = "strip_metadata"
        const val KEY_SELECTED_CHAPTERS = "selected_chapters"

        fun start(
            context: Context,
            onlyWhileCharging: Boolean = true,
            options: JobOptions = JobOptions(),
        ) {
            val constraints = Constraints.Builder().apply {
                if (onlyWhileCharging) {
                    setRequiresCharging(true)
                }
            }.build()

            val inputData = androidx.work.Data.Builder().apply {
                if (options.format != null) putString(KEY_FORMAT, options.format)
                if (options.quality != null) putInt(KEY_QUALITY, options.quality)
                if (options.effort != null) putInt(KEY_EFFORT, options.effort)
                if (options.autoGrayscale != null) putBoolean(KEY_AUTO_GRAYSCALE, options.autoGrayscale)
                if (options.stripMetadata != null) putBoolean(KEY_STRIP_METADATA, options.stripMetadata)
                if (options.selectedChapterUris != null) putStringArray(KEY_SELECTED_CHAPTERS, options.selectedChapterUris.toTypedArray())
            }.build()

            val request = OneTimeWorkRequestBuilder<DownloadOptimizerJob>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .any { it.state == WorkInfo.State.RUNNING }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        }

        fun UniFile.toLocalFile(): File? {
            val path = filePath
            if (path != null) {
                val f = File(path)
                if (f.exists() && f.canRead()) return f
            }
            val uri = this.uri
            if (uri.scheme == "file") {
                val f = uri.path?.let { File(it) }
                if (f != null && f.exists() && f.canRead()) return f
            }
            if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
                return resolveExternalDocFile(uri)
            }
            return null
        }

        private fun resolveExternalDocFile(uri: android.net.Uri): File? {
            val pathStr = uri.path.orEmpty()
            val docId = try {
                when {
                    pathStr.contains("/document/") -> pathStr.substringAfter("/document/")
                    pathStr.contains("/tree/") -> pathStr.substringAfter("/tree/")
                    else -> null
                }?.let { android.net.Uri.decode(it) }
            } catch (_: Throwable) {
                null
            } ?: return null

            val file = if (docId.startsWith("primary:", ignoreCase = true)) {
                val relPath = docId.substringAfter(':')
                File(android.os.Environment.getExternalStorageDirectory(), relPath)
            } else if (docId.contains(':')) {
                val parts = docId.split(':', limit = 2)
                File("/storage/${parts[0]}/${parts[1]}")
            } else {
                null
            }
            return file?.takeIf { it.exists() && it.canRead() }
        }

        fun getEligibleChapters(context: Context, downloadsDir: UniFile): List<UniFile> {
            val localEligible = getEligibleChaptersLocal(context, downloadsDir)
            if (localEligible.isNotEmpty()) return localEligible

            return getEligibleChaptersSaf(context, downloadsDir)
        }

        private fun getEligibleChaptersLocal(context: Context, downloadsDir: UniFile): List<UniFile> {
            val rootFile = downloadsDir.toLocalFile()
            val localSourceDirs = rootFile?.listFiles { f -> f.isDirectory && f.name.isNotBlank() } ?: return emptyList()
            return localSourceDirs.flatMap { sourceDir ->
                val mangaDirs = sourceDir.listFiles { f -> f.isDirectory && f.name.isNotBlank() } ?: emptyArray()
                mangaDirs.flatMap { collectMangaEligibleChapters(context, it) }
            }
        }

        private fun collectMangaEligibleChapters(context: Context, mangaDir: File): List<UniFile> {
            val chapterFiles = mangaDir.listFiles { f ->
                !f.name.endsWith(Downloader.TMP_DIR_SUFFIX) &&
                    (f.isDirectory || (f.isFile && f.extension.equals("cbz", ignoreCase = true)))
            } ?: emptyArray()
            return chapterFiles.mapNotNull { UniFile.fromFile(it) }.filter { isChapterEligible(context, it) }
        }

        private fun getEligibleChaptersSaf(context: Context, downloadsDir: UniFile): List<UniFile> {
            val sourceDirs = downloadsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }
            return sourceDirs.flatMap { sourceDir ->
                val mangaDirs = sourceDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }
                mangaDirs.flatMap { collectSafMangaEligibleChapters(context, it) }
            }
        }

        private fun collectSafMangaEligibleChapters(context: Context, mangaDir: UniFile): List<UniFile> {
            val chapterEntries = mangaDir.listFiles().orEmpty().filter {
                !it.name.orEmpty().endsWith(Downloader.TMP_DIR_SUFFIX) &&
                    (it.isDirectory || (it.isFile && it.extension.equals("cbz", ignoreCase = true)))
            }
            return chapterEntries.filter { isChapterEligible(context, it) }
        }

        suspend fun getEligibleChaptersBySeries(context: Context, downloadsDir: UniFile): List<OptimizableSeries> = withContext(Dispatchers.IO) {
            logcat(LogPriority.INFO) { "getEligibleChaptersBySeries: downloadsDir=${downloadsDir.uri}" }
            val mangaList = try {
                Injekt.get<GetAllManga>().await()
            } catch (_: Throwable) {
                emptyList()
            }
            val getChaptersByMangaId = try {
                Injekt.get<GetChaptersByMangaId>()
            } catch (_: Throwable) {
                null
            }

            val mangaByOgTitle = mangaList.associateBy { DiskUtil.buildValidFilename(it.ogTitle) }
            val mangaByTitle = mangaList.associateBy { DiskUtil.buildValidFilename(it.title) }
            val findManga = { name: String -> mangaByOgTitle[name] ?: mangaByTitle[name] }

            val fastResults = getEligibleChaptersBySeriesFastPath(context, downloadsDir, findManga, getChaptersByMangaId)
            if (fastResults.isNotEmpty()) {
                return@withContext fastResults
            }

            return@withContext getEligibleChaptersBySeriesFallbackPath(context, downloadsDir, findManga, getChaptersByMangaId)
        }

        private suspend fun getEligibleChaptersBySeriesFastPath(
            context: Context,
            downloadsDir: UniFile,
            findManga: (String) -> Manga?,
            getChaptersByMangaId: GetChaptersByMangaId?,
        ): List<OptimizableSeries> = coroutineScope {
            val rootFile = downloadsDir.toLocalFile()
            val localSourceDirs = rootFile?.listFiles { f -> f.isDirectory && f.name.isNotBlank() }
            if (localSourceDirs.isNullOrEmpty()) return@coroutineScope emptyList()

            logcat(LogPriority.INFO) { "Using fast local file path with ${localSourceDirs.size} source dirs" }
            val allMangaDirs = localSourceDirs.flatMap { it.listFiles { f -> f.isDirectory && f.name.isNotBlank() }?.toList() ?: emptyList() }

            allMangaDirs.map { mangaDir ->
                async(Dispatchers.IO) {
                    processMangaDirLocal(context, mangaDir, findManga, getChaptersByMangaId)
                }
            }.awaitAll().filterNotNull()
        }

        private suspend fun processMangaDirLocal(
            context: Context,
            mangaDir: java.io.File,
            findManga: (String) -> Manga?,
            getChaptersByMangaId: GetChaptersByMangaId?,
        ): OptimizableSeries? {
            val mangaDirName = mangaDir.name
            val matchedManga = findManga(mangaDirName)
            val readChapterNames = getReadChapterNames(matchedManga, getChaptersByMangaId)

            val chapterFiles = mangaDir.listFiles { f ->
                !f.name.endsWith(Downloader.TMP_DIR_SUFFIX) && (f.isDirectory || (f.isFile && f.extension.equals("cbz", ignoreCase = true)))
            } ?: emptyArray()

            val eligibleChapters = mutableListOf<OptimizableChapter>()
            for (chapterFile in chapterFiles) {
                val uni = UniFile.fromFile(chapterFile) ?: continue
                if (isChapterEligible(context, uni)) {
                    val isCbz = chapterFile.isFile && chapterFile.extension.equals("cbz", ignoreCase = true)
                    val sizeBytes = if (isCbz) chapterFile.length() else chapterFile.listFiles { f -> f.isFile }?.sumOf { it.length() } ?: 0L
                    val chapterName = chapterFile.nameWithoutExtension.ifEmpty { chapterFile.name }
                    eligibleChapters.add(
                        OptimizableChapter(
                            uriString = uni.uri.toString(),
                            name = chapterName,
                            sizeBytes = sizeBytes,
                            isCbz = isCbz,
                            isRead = readChapterNames.contains(chapterName.lowercase()),
                        ),
                    )
                }
            }

            if (eligibleChapters.isNotEmpty()) {
                return OptimizableSeries(id = mangaDir.absolutePath, title = matchedManga?.title ?: mangaDirName, manga = matchedManga, chapters = eligibleChapters)
            }
            return null
        }

        private suspend fun getEligibleChaptersBySeriesFallbackPath(
            context: Context,
            downloadsDir: UniFile,
            findManga: (String) -> Manga?,
            getChaptersByMangaId: GetChaptersByMangaId?,
        ): List<OptimizableSeries> = coroutineScope {
            logcat(LogPriority.INFO) { "Using UniFile SAF traversal for downloads directory" }
            val sourceDirs = downloadsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }
            val allMangaDirs = sourceDirs.flatMap { it.listFiles().orEmpty().filter { m -> m.isDirectory && !m.name.isNullOrBlank() } }

            allMangaDirs.map { mangaDir ->
                async(Dispatchers.IO) {
                    processMangaDirSaf(context, mangaDir, findManga, getChaptersByMangaId)
                }
            }.awaitAll().filterNotNull()
        }

        private suspend fun processMangaDirSaf(
            context: Context,
            mangaDir: UniFile,
            findManga: (String) -> Manga?,
            getChaptersByMangaId: GetChaptersByMangaId?,
        ): OptimizableSeries? {
            val mangaDirName = mangaDir.name.orEmpty()
            val matchedManga = findManga(mangaDirName)
            val readChapterNames = getReadChapterNames(matchedManga, getChaptersByMangaId)

            val chapterEntries = mangaDir.listFiles().orEmpty().filter {
                !it.name.orEmpty().endsWith(Downloader.TMP_DIR_SUFFIX) && (it.isDirectory || (it.isFile && it.extension.equals("cbz", ignoreCase = true)))
            }

            val eligibleChapters = mutableListOf<OptimizableChapter>()
            for (chapter in chapterEntries) {
                if (isChapterEligible(context, chapter)) {
                    val isCbz = chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)
                    val sizeBytes = if (isCbz) chapter.length() else chapter.listFiles()?.sumOf { it.length() } ?: 0L
                    val chapterName = chapter.nameWithoutExtension ?: chapter.name.orEmpty()
                    eligibleChapters.add(
                        OptimizableChapter(
                            uriString = chapter.uri.toString(),
                            name = chapterName,
                            sizeBytes = sizeBytes,
                            isCbz = isCbz,
                            isRead = readChapterNames.contains(chapterName.lowercase()),
                        ),
                    )
                }
            }

            if (eligibleChapters.isNotEmpty()) {
                return OptimizableSeries(id = mangaDir.uri.toString(), title = matchedManga?.title ?: mangaDirName, manga = matchedManga, chapters = eligibleChapters)
            }
            return null
        }

        private suspend fun getReadChapterNames(manga: Manga?, getChaptersByMangaId: GetChaptersByMangaId?): Set<String> {
            if (manga == null || getChaptersByMangaId == null) return emptySet()
            return try {
                getChaptersByMangaId.await(manga.id)
                    .filter { it.read }
                    .flatMap { listOf(it.name.lowercase(), DiskUtil.buildValidFilename(it.name).lowercase()) }
                    .toHashSet()
            } catch (_: Throwable) {
                emptySet()
            }
        }

        private fun isChapterEligible(context: Context, chapter: UniFile): Boolean {
            return try {
                val localFile = chapter.toLocalFile()
                if (chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)) {
                    isCbzEligible(context, chapter, localFile)
                } else if (chapter.isDirectory) {
                    isDirectoryEligible(chapter, localFile)
                } else {
                    false
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to check eligibility for ${chapter.name}" }
                false
            }
        }

        private fun isCbzEligible(context: Context, chapter: UniFile, localFile: File?): Boolean {
            if (localFile != null && localFile.canRead()) {
                val eligible = isCbzEligibleFast(localFile)
                if (eligible != null) return eligible
            }

            val streamEligible = isCbzEligibleStream(chapter)
            if (streamEligible != null) return streamEligible

            return isCbzEligibleArchiveReader(context, chapter)
        }

        private fun isCbzEligibleFast(localFile: File): Boolean? {
            return try {
                ZipFile(localFile).use { zf ->
                    if (zf.getEntry(OPTIMIZED_MARKER) != null) return false
                    val counts = ImageCounts()
                    if (!countZipFileEntries(zf, counts)) return false
                    counts.isEligible
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "ZipFile check failed for ${localFile.name}, trying ZipInputStream" }
                null
            }
        }

        private fun countZipFileEntries(zf: ZipFile, counts: ImageCounts): Boolean {
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name == OPTIMIZED_MARKER || name.endsWith("/$OPTIMIZED_MARKER")) return false
                counts.record(name.substringAfterLast('.', "").lowercase())
            }
            return true
        }

        private fun isCbzEligibleStream(chapter: UniFile): Boolean? {
            return try {
                chapter.openInputStream()?.buffered()?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        countZipStreamEntries(zis, ImageCounts())
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "ZipInputStream check failed for ${chapter.name}" }
                null
            }
        }

        private fun countZipStreamEntries(zis: ZipInputStream, counts: ImageCounts): Boolean? {
            var foundAny = false
            var entry = zis.nextEntry
            while (entry != null) {
                foundAny = true
                val name = entry.name
                if (name == OPTIMIZED_MARKER || name.endsWith("/$OPTIMIZED_MARKER")) return false
                counts.record(name.substringAfterLast('.', "").lowercase())
                entry = zis.nextEntry
            }
            return if (foundAny) counts.isEligible else null
        }

        private fun isCbzEligibleArchiveReader(context: Context, chapter: UniFile): Boolean {
            return try {
                val counts = ImageCounts()
                var isOptimized = false
                chapter.archiveReader(context).use { reader ->
                    reader.useEntries { entries ->
                        entries.forEach { entry ->
                            val name = entry.name
                            if (name == OPTIMIZED_MARKER || name.endsWith("/$OPTIMIZED_MARKER")) {
                                isOptimized = true
                            }
                            counts.record(name.substringAfterLast('.', "").lowercase())
                        }
                    }
                }
                if (isOptimized) return false
                counts.isEligible
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "ArchiveReader check failed for ${chapter.name}" }
                false
            }
        }

        private fun isDirectoryEligible(chapter: UniFile, localFile: File?): Boolean {
            if (localFile != null && localFile.canRead()) {
                if (File(localFile, OPTIMIZED_MARKER).exists()) return false
                val files = localFile.listFiles { f -> f.isFile } ?: emptyArray()
                val counts = ImageCounts()
                for (file in files) {
                    counts.record(file.extension.lowercase())
                }
                return counts.isEligible
            }

            if (chapter.findFile(OPTIMIZED_MARKER) != null) return false
            val files = chapter.listFiles().orEmpty()
            val counts = ImageCounts()
            for (file in files) {
                counts.record(file.extension?.lowercase().orEmpty())
            }
            return counts.isEligible
        }
    }
}

data class JobOptions(
    val format: String? = null,
    val quality: Int? = null,
    val effort: Int? = null,
    val autoGrayscale: Boolean? = null,
    val stripMetadata: Boolean? = null,
    val selectedChapterUris: Set<String>? = null,
)

data class OptimizableChapter(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val isCbz: Boolean,
    val isRead: Boolean,
)

data class OptimizableSeries(
    val id: String,
    val title: String,
    val manga: Manga?,
    val chapters: List<OptimizableChapter>,
)

object DownloadOptimizerState {
    val eligibleSeries = MutableStateFlow<List<OptimizableSeries>?>(null)
    val selectedChapterUris = MutableStateFlow<Set<String>?>(null)

    val format = MutableStateFlow("")
    val quality = MutableStateFlow(0)
    val effort = MutableStateFlow(0)
    val autoGrayscale = MutableStateFlow<Boolean?>(null)
    val stripMetadata = MutableStateFlow<Boolean?>(null)
    val onlyWhileCharging = MutableStateFlow(false)

    fun clearCache() {
        eligibleSeries.value = null
        selectedChapterUris.value = null
    }

    fun reset() {
        format.value = ""
        quality.value = 0
        effort.value = 0
        autoGrayscale.value = null
        stripMetadata.value = null
        onlyWhileCharging.value = false
        clearCache()
    }
}
// KMK <--
