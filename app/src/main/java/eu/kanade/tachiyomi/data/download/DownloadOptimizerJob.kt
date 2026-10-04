// KMK -->
package eu.kanade.tachiyomi.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
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
        chapterIndex: Int = 0,
        totalChapters: Int = 0,
        seriesTitle: String? = null,
        chapterTitle: String = "",
        isExtracting: Boolean = false,
        currentPage: Int = 0,
        totalPages: Int = 0,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastNotificationTime < 350L)) {
            return
        }
        lastNotificationTime = now

        try {
            val notification = createNotification(
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                seriesTitle = seriesTitle,
                chapterTitle = chapterTitle,
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
        chapterIndex: Int = 0,
        totalChapters: Int = 0,
        seriesTitle: String? = null,
        chapterTitle: String = "",
        isExtracting: Boolean = false,
        currentPage: Int = 0,
        totalPages: Int = 0,
    ): Notification {
        val cancelIntent = NotificationReceiver.cancelDownloadOptimizerPendingBroadcast(context)
        val title = if (totalChapters > 0) {
            context.stringResource(KMR.strings.optimize_notification_title, chapterIndex, totalChapters)
        } else {
            context.stringResource(KMR.strings.optimize_notification_channel)
        }

        val baseText = if (!seriesTitle.isNullOrBlank() && !chapterTitle.isNullOrBlank()) {
            "$seriesTitle • $chapterTitle"
        } else if (!chapterTitle.isNullOrBlank()) {
            chapterTitle
        } else if (totalChapters > 0) {
            context.stringResource(KMR.strings.optimize_notification_running, chapterIndex, totalChapters)
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
            if (isExtracting || (totalPages <= 0 && totalChapters <= 0)) {
                setProgress(0, 0, true)
            } else if (totalPages > 0) {
                setProgress(totalPages, currentPage, false)
            } else {
                setProgress(totalChapters, chapterIndex, false)
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
        val quality = inputData.getInt(KEY_QUALITY, -1).takeIf { it != -1 }
            ?: downloadPreferences.downloadCompressionQuality().get()
        val autoGrayscale = if (inputData.keyValueMap.containsKey(KEY_AUTO_GRAYSCALE)) {
            inputData.getBoolean(KEY_AUTO_GRAYSCALE, true)
        } else {
            downloadPreferences.autoGrayscaleBWManga().get()
        }
        val stripMetadata = if (inputData.keyValueMap.containsKey(KEY_STRIP_METADATA)) {
            inputData.getBoolean(KEY_STRIP_METADATA, true)
        } else {
            downloadPreferences.stripImageMetadata().get()
        }

        val allChapters = getEligibleChapters(context, downloadsDir)
        val selectedUris = inputData.getStringArray(KEY_SELECTED_CHAPTERS)?.toSet()
        val chapters = if (!selectedUris.isNullOrEmpty()) {
            allChapters.filter { chapter ->
                val uriStr = chapter.uri.toString()
                val filePath = chapter.filePath
                val name = chapter.name
                selectedUris.contains(uriStr) ||
                    (filePath != null && selectedUris.contains(filePath)) ||
                    (name != null && selectedUris.any { it.endsWith("/$name") || it.endsWith("%2F$name") })
            }
        } else {
            allChapters
        }

        logcat(LogPriority.INFO) { "DownloadOptimizerJob: found ${chapters.size} eligible chapters to optimize (out of ${allChapters.size} total)" }
        if (chapters.isEmpty()) {
            return Result.success()
        }

        var totalSavedBytes = 0L

        try {
            for ((index, chapter) in chapters.withIndex()) {
                if (isStopped) {
                    notifyCanceled()
                    return Result.failure()
                }

                val chapterIndex = index + 1
                val seriesTitle = chapter.parentFile?.name?.takeIf { it != "downloads" }
                val chapterTitle = chapter.nameWithoutExtension ?: chapter.name.orEmpty()

                try {
                    val saved = if (chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)) {
                        optimizeCbzChapter(
                            cbzFile = chapter,
                            format = format,
                            quality = quality,
                            autoGrayscale = autoGrayscale,
                            stripMetadata = stripMetadata,
                            chapterIndex = chapterIndex,
                            totalChapters = chapters.size,
                            seriesTitle = seriesTitle,
                            chapterTitle = chapterTitle,
                        )
                    } else if (chapter.isDirectory) {
                        optimizeDirectoryChapter(
                            chapterDir = chapter,
                            format = format,
                            quality = quality,
                            autoGrayscale = autoGrayscale,
                            stripMetadata = stripMetadata,
                            chapterIndex = chapterIndex,
                            totalChapters = chapters.size,
                            seriesTitle = seriesTitle,
                            chapterTitle = chapterTitle,
                        )
                    } else {
                        0L
                    }
                    if (saved > 0) {
                        totalSavedBytes += saved
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to optimize chapter: ${chapter.name}" }
                }
            }
        } finally {
            context.cancelNotification(Notifications.ID_STORAGE_OPTIMIZER_PROGRESS)
        }

        notifyComplete(totalSavedBytes)
        DownloadOptimizerState.clearCache()
        return Result.success()
    }

    private fun optimizeDirectoryChapter(
        chapterDir: UniFile,
        format: String,
        quality: Int,
        autoGrayscale: Boolean,
        stripMetadata: Boolean,
        chapterIndex: Int,
        totalChapters: Int,
        seriesTitle: String?,
        chapterTitle: String,
    ): Long {
        var savedBytes = 0L
        val files = chapterDir.listFiles().orEmpty().filter {
            !it.name.orEmpty().endsWith(".tmp") && !it.name.orEmpty().endsWith(".tmp_comp") && ImageUtil.isImage(it.name)
        }

        val totalImages = files.size
        var currentImage = 0

        updateProgressNotification(
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            seriesTitle = seriesTitle,
            chapterTitle = chapterTitle,
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
                    format = format,
                    quality = quality,
                    autoGrayscale = autoGrayscale,
                    stripMetadata = stripMetadata,
                )
                if (result.compressed) {
                    savedBytes += (result.originalSize - result.finalSize)
                }
            }
            currentImage++
            updateProgressNotification(
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                seriesTitle = seriesTitle,
                chapterTitle = chapterTitle,
                isExtracting = false,
                currentPage = currentImage,
                totalPages = totalImages,
                force = (currentImage == 1 || currentImage == totalImages),
            )
        }
        return savedBytes
    }

    private fun optimizeCbzChapter(
        cbzFile: UniFile,
        format: String,
        quality: Int,
        autoGrayscale: Boolean,
        stripMetadata: Boolean,
        chapterIndex: Int,
        totalChapters: Int,
        seriesTitle: String?,
        chapterTitle: String,
    ): Long {
        val parentDir = cbzFile.parentFile ?: return 0L
        val originalSize = cbzFile.length()
        val finalName = cbzFile.name ?: return 0L
        val tempExtractDir = File(context.cacheDir, "cbz_opt_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        updateProgressNotification(
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            seriesTitle = seriesTitle,
            chapterTitle = chapterTitle,
            isExtracting = true,
            force = true,
        )

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

        try {
            var isEncrypted = false
            try {
                cbzFile.archiveReader(context).use { reader ->
                    isEncrypted = reader.encrypted
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to check encryption for ${cbzFile.name}" }
            }

            var extractSuccess = false
            if (!isEncrypted) {
                try {
                    ZipFile(localInputFile).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory) {
                                val outFile = File(tempExtractDir, entry.name)
                                outFile.parentFile?.mkdirs()
                                zf.getInputStream(entry).use { inStream ->
                                    outFile.outputStream().buffered().use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                            }
                        }
                    }
                    extractSuccess = true
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "ZipFile extraction failed for ${cbzFile.name}, trying ArchiveReader" }
                }
            }

            if (!extractSuccess) {
                cbzFile.archiveReader(context).use { reader ->
                    reader.useEntries { entries ->
                        entries.filter { it.isFile }.forEach { entry ->
                            val outFile = File(tempExtractDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().buffered().use { out ->
                                reader.getInputStream(entry.name)?.use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                    }
                }
            }

            logcat(LogPriority.INFO) { "Extracted files for ${cbzFile.name} to $tempExtractDir: ${tempExtractDir.list()?.size} entries" }

            // Compress uncompressed images in extracted directory
            var anyCompressed = false
            val imageFiles = tempExtractDir.walkTopDown().filter { file ->
                file.isFile && !file.name.endsWith(".tmp") && !file.name.endsWith(".tmp_comp") && ImageUtil.isImage(file.name) { file.inputStream() }
            }.toList()
            val totalImages = imageFiles.size
            var currentImage = 0

            updateProgressNotification(
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                seriesTitle = seriesTitle,
                chapterTitle = chapterTitle,
                isExtracting = false,
                currentPage = 0,
                totalPages = totalImages,
                force = true,
            )

            for (file in imageFiles) {
                if (isStopped) return 0L
                if (!ImageCompressor.isAlreadyCompressed(file)) {
                    val res = ImageCompressor.compressFile(
                        file = file,
                        format = format,
                        quality = quality,
                        autoGrayscale = autoGrayscale,
                        stripMetadata = stripMetadata,
                    )
                    logcat(LogPriority.INFO) { "Compression result for ${file.name}: success=${res.success}, compressed=${res.compressed}, orig=${res.originalSize}, final=${res.finalSize}" }
                    if (res.compressed) {
                        anyCompressed = true
                    }
                }
                currentImage++
                updateProgressNotification(
                    chapterIndex = chapterIndex,
                    totalChapters = totalChapters,
                    seriesTitle = seriesTitle,
                    chapterTitle = chapterTitle,
                    isExtracting = false,
                    currentPage = currentImage,
                    totalPages = totalImages,
                    force = (currentImage == 1 || currentImage == totalImages),
                )
            }

            if (!anyCompressed) {
                logcat(LogPriority.INFO) { "No images were compressed for ${cbzFile.name}" }
                return 0L
            }

            // Re-pack into temporary CBZ
            val tempCbzName = "$finalName.tmp_opt.cbz"
            val tempCbz = parentDir.createFile(tempCbzName) ?: return 0L

            if (isEncrypted) {
                ZipWriter(context, tempCbz, true).use { writer ->
                    tempExtractDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            UniFile.fromFile(file)?.let { writer.write(it) }
                        }
                    }
                }
            } else {
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

            val newSize = tempCbz.length()
            logcat(LogPriority.INFO) { "CBZ optimization for ${cbzFile.name}: originalSize=$originalSize, newSize=$newSize" }
            if (newSize > 0 && newSize < originalSize) {
                cbzFile.delete()
                tempCbz.renameTo(finalName)
                return originalSize - newSize
            } else {
                tempCbz.delete()
                return 0L
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to optimize CBZ: ${cbzFile.name}" }
            return 0L
        } finally {
            if (isTempInputFile) {
                localInputFile.delete()
            }
            tempExtractDir.deleteRecursively()
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
        const val KEY_FORMAT = "format"
        const val KEY_QUALITY = "quality"
        const val KEY_EFFORT = "effort"
        const val KEY_AUTO_GRAYSCALE = "auto_grayscale"
        const val KEY_STRIP_METADATA = "strip_metadata"
        const val KEY_SELECTED_CHAPTERS = "selected_chapters"

        fun start(
            context: Context,
            onlyWhileCharging: Boolean = true,
            format: String? = null,
            quality: Int? = null,
            effort: Int? = null,
            autoGrayscale: Boolean? = null,
            stripMetadata: Boolean? = null,
            selectedChapterUris: Set<String>? = null,
        ) {
            val constraints = Constraints.Builder().apply {
                if (onlyWhileCharging) {
                    setRequiresCharging(true)
                }
            }.build()

            val inputData = androidx.work.Data.Builder().apply {
                if (format != null) putString(KEY_FORMAT, format)
                if (quality != null) putInt(KEY_QUALITY, quality)
                if (effort != null) putInt(KEY_EFFORT, effort)
                if (autoGrayscale != null) putBoolean(KEY_AUTO_GRAYSCALE, autoGrayscale)
                if (stripMetadata != null) putBoolean(KEY_STRIP_METADATA, stripMetadata)
                if (selectedChapterUris != null) putStringArray(KEY_SELECTED_CHAPTERS, selectedChapterUris.toTypedArray())
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
                val pathStr = uri.path.orEmpty()
                val docId = try {
                    when {
                        pathStr.contains("/document/") -> pathStr.substringAfter("/document/")
                        pathStr.contains("/tree/") -> pathStr.substringAfter("/tree/")
                        else -> null
                    }?.let { android.net.Uri.decode(it) }
                } catch (_: Throwable) {
                    null
                }

                if (docId != null) {
                    val file = if (docId.startsWith("primary:", ignoreCase = true)) {
                        val relPath = docId.substringAfter(':')
                        File(android.os.Environment.getExternalStorageDirectory(), relPath)
                    } else if (docId.contains(':')) {
                        val parts = docId.split(':', limit = 2)
                        File("/storage/${parts[0]}/${parts[1]}")
                    } else {
                        null
                    }
                    if (file != null && file.exists() && file.canRead()) {
                        return file
                    }
                }
            }
            return null
        }

        fun getEligibleChapters(context: Context, downloadsDir: UniFile): List<UniFile> {
            val rootFile = downloadsDir.toLocalFile()
            val localSourceDirs = rootFile?.listFiles { f -> f.isDirectory && f.name.isNotBlank() }
            if (!localSourceDirs.isNullOrEmpty()) {
                val eligible = mutableListOf<UniFile>()
                for (sourceDir in localSourceDirs) {
                    val mangaDirs = sourceDir.listFiles { f -> f.isDirectory && f.name.isNotBlank() } ?: emptyArray()
                    for (mangaDir in mangaDirs) {
                        val chapterFiles = mangaDir.listFiles { f ->
                            !f.name.endsWith(Downloader.TMP_DIR_SUFFIX) &&
                                (f.isDirectory || (f.isFile && f.extension.equals("cbz", ignoreCase = true)))
                        } ?: emptyArray()
                        for (chapterFile in chapterFiles) {
                            val uni = UniFile.fromFile(chapterFile) ?: continue
                            if (isChapterEligible(context, uni)) {
                                eligible.add(uni)
                            }
                        }
                    }
                }
                if (eligible.isNotEmpty()) {
                    return eligible
                }
            }

            val eligible = mutableListOf<UniFile>()
            val sourceDirs = downloadsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }

            for (sourceDir in sourceDirs) {
                val mangaDirs = sourceDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }
                for (mangaDir in mangaDirs) {
                    val chapterEntries = mangaDir.listFiles().orEmpty().filter {
                        !it.name.orEmpty().endsWith(Downloader.TMP_DIR_SUFFIX) &&
                            (it.isDirectory || (it.isFile && it.extension.equals("cbz", ignoreCase = true)))
                    }

                    for (chapter in chapterEntries) {
                        if (isChapterEligible(context, chapter)) {
                            eligible.add(chapter)
                        }
                    }
                }
            }
            return eligible
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

            // Precompute O(1) filename-to-manga lookup maps
            val mangaByOgTitle = mangaList.associateBy { DiskUtil.buildValidFilename(it.ogTitle) }
            val mangaByTitle = mangaList.associateBy { DiskUtil.buildValidFilename(it.title) }

            fun findManga(name: String): Manga? = mangaByOgTitle[name] ?: mangaByTitle[name]

            // 1. FAST PATH: Direct java.io.File access when a local filesystem path is available and listable
            val rootFile = downloadsDir.toLocalFile()
            val localSourceDirs = rootFile?.listFiles { f -> f.isDirectory && f.name.isNotBlank() }

            if (!localSourceDirs.isNullOrEmpty()) {
                logcat(LogPriority.INFO) { "Using fast local file path with ${localSourceDirs.size} source dirs" }
                val allMangaDirs = localSourceDirs.flatMap { it.listFiles { f -> f.isDirectory && f.name.isNotBlank() }?.toList() ?: emptyList() }

                val results = coroutineScope {
                    allMangaDirs.map { mangaDir ->
                        async(Dispatchers.IO) {
                            val mangaDirName = mangaDir.name
                            val matchedManga = findManga(mangaDirName)

                            val readChapterNames = if (matchedManga != null && getChaptersByMangaId != null) {
                                try {
                                    getChaptersByMangaId.await(matchedManga.id)
                                        .filter { it.read }
                                        .flatMap { listOf(it.name.lowercase(), DiskUtil.buildValidFilename(it.name).lowercase()) }
                                        .toHashSet()
                                } catch (_: Throwable) {
                                    emptySet()
                                }
                            } else {
                                emptySet()
                            }

                            val chapterFiles = mangaDir.listFiles { f ->
                                !f.name.endsWith(Downloader.TMP_DIR_SUFFIX) &&
                                    (f.isDirectory || (f.isFile && f.extension.equals("cbz", ignoreCase = true)))
                            } ?: emptyArray()

                            val eligibleChapters = mutableListOf<OptimizableChapter>()
                            for (chapterFile in chapterFiles) {
                                val uni = UniFile.fromFile(chapterFile) ?: continue
                                if (isChapterEligible(context, uni)) {
                                    val isCbz = chapterFile.isFile && chapterFile.extension.equals("cbz", ignoreCase = true)
                                    val sizeBytes = if (isCbz) {
                                        chapterFile.length()
                                    } else {
                                        chapterFile.listFiles { f -> f.isFile }?.sumOf { it.length() } ?: 0L
                                    }
                                    val chapterName = chapterFile.nameWithoutExtension.ifEmpty { chapterFile.name }
                                    val isRead = readChapterNames.contains(chapterName.lowercase())
                                    val uriString = uni.uri.toString()

                                    eligibleChapters.add(
                                        OptimizableChapter(
                                            uriString = uriString,
                                            name = chapterName,
                                            sizeBytes = sizeBytes,
                                            isCbz = isCbz,
                                            isRead = isRead,
                                        ),
                                    )
                                }
                            }

                            if (eligibleChapters.isNotEmpty()) {
                                OptimizableSeries(
                                    id = mangaDir.absolutePath,
                                    title = matchedManga?.title ?: mangaDirName,
                                    manga = matchedManga,
                                    chapters = eligibleChapters,
                                )
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                if (results.isNotEmpty()) {
                    return@withContext results
                }
            }

            // 2. FALLBACK PATH: SAF / DocumentFile UniFile traversal (parallelized)
            logcat(LogPriority.INFO) { "Using UniFile SAF traversal for downloads directory" }
            val sourceDirs = downloadsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.isNullOrBlank() }
            logcat(LogPriority.INFO) { "Found ${sourceDirs.size} SAF source directories: ${sourceDirs.map { it.name }}" }
            val allMangaDirs = sourceDirs.flatMap { it.listFiles().orEmpty().filter { m -> m.isDirectory && !m.name.isNullOrBlank() } }
            logcat(LogPriority.INFO) { "Found ${allMangaDirs.size} SAF manga directories: ${allMangaDirs.map { it.name }}" }

            coroutineScope {
                allMangaDirs.map { mangaDir ->
                    async(Dispatchers.IO) {
                        val mangaDirName = mangaDir.name.orEmpty()
                        val matchedManga = findManga(mangaDirName)

                        val readChapterNames = if (matchedManga != null && getChaptersByMangaId != null) {
                            try {
                                getChaptersByMangaId.await(matchedManga.id)
                                    .filter { it.read }
                                    .flatMap { listOf(it.name.lowercase(), DiskUtil.buildValidFilename(it.name).lowercase()) }
                                    .toHashSet()
                            } catch (_: Throwable) {
                                emptySet()
                            }
                        } else {
                            emptySet()
                        }

                        val chapterEntries = mangaDir.listFiles().orEmpty().filter {
                            !it.name.orEmpty().endsWith(Downloader.TMP_DIR_SUFFIX) &&
                                (it.isDirectory || (it.isFile && it.extension.equals("cbz", ignoreCase = true)))
                        }
                        logcat(LogPriority.INFO) { "Manga $mangaDirName has ${chapterEntries.size} chapter entries" }

                        val eligibleChapters = mutableListOf<OptimizableChapter>()
                        for (chapter in chapterEntries) {
                            val eligible = isChapterEligible(context, chapter)
                            logcat(LogPriority.INFO) { "Chapter ${chapter.name} in $mangaDirName: isEligible=$eligible" }
                            if (eligible) {
                                val isCbz = chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)
                                val sizeBytes = if (isCbz) {
                                    chapter.length()
                                } else {
                                    chapter.listFiles()?.sumOf { it.length() } ?: 0L
                                }
                                val chapterName = chapter.nameWithoutExtension ?: chapter.name.orEmpty()
                                val isRead = readChapterNames.contains(chapterName.lowercase())

                                eligibleChapters.add(
                                    OptimizableChapter(
                                        uriString = chapter.uri.toString(),
                                        name = chapterName,
                                        sizeBytes = sizeBytes,
                                        isCbz = isCbz,
                                        isRead = isRead,
                                    ),
                                )
                            }
                        }

                        if (eligibleChapters.isNotEmpty()) {
                            OptimizableSeries(
                                id = mangaDir.uri.toString(),
                                title = matchedManga?.title ?: mangaDirName,
                                manga = matchedManga,
                                chapters = eligibleChapters,
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }

        private fun isChapterEligible(context: Context, chapter: UniFile): Boolean {
            return try {
                val localFile = chapter.toLocalFile()
                if (chapter.isFile && chapter.extension.equals("cbz", ignoreCase = true)) {
                    var uncompressedCount = 0
                    var compressedCount = 0

                    if (localFile != null && localFile.canRead()) {
                        try {
                            ZipFile(localFile).use { zf ->
                                val entries = zf.entries()
                                while (entries.hasMoreElements()) {
                                    val ext = entries.nextElement().name.substringAfterLast('.', "").lowercase()
                                    if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "bmp") {
                                        uncompressedCount++
                                    } else if (ext == "webp" || ext == "avif") {
                                        compressedCount++
                                    }
                                }
                            }
                            val total = uncompressedCount + compressedCount
                            return total > 0 && (uncompressedCount.toDouble() / total > 0.10)
                        } catch (e: Exception) {
                            logcat(LogPriority.DEBUG, e) { "ZipFile check failed for ${chapter.name}, trying ZipInputStream" }
                        }
                    }

                    try {
                        uncompressedCount = 0
                        compressedCount = 0
                        var foundAny = false
                        chapter.openInputStream()?.buffered()?.use { stream ->
                            ZipInputStream(stream).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    foundAny = true
                                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                                    if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "bmp") {
                                        uncompressedCount++
                                    } else if (ext == "webp" || ext == "avif") {
                                        compressedCount++
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        }
                        if (foundAny) {
                            val total = uncompressedCount + compressedCount
                            return total > 0 && (uncompressedCount.toDouble() / total > 0.10)
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.DEBUG, e) { "ZipInputStream check failed for ${chapter.name}, trying ArchiveReader" }
                    }

                    try {
                        uncompressedCount = 0
                        compressedCount = 0
                        chapter.archiveReader(context).use { reader ->
                            reader.useEntries { entries ->
                                entries.forEach { entry ->
                                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                                    if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "bmp") {
                                        uncompressedCount++
                                    } else if (ext == "webp" || ext == "avif") {
                                        compressedCount++
                                    }
                                }
                            }
                        }
                        val total = uncompressedCount + compressedCount
                        total > 0 && (uncompressedCount.toDouble() / total > 0.10)
                    } catch (e: Throwable) {
                        logcat(LogPriority.DEBUG, e) { "ArchiveReader check failed for ${chapter.name}" }
                        false
                    }
                } else if (chapter.isDirectory) {
                    var uncompressedCount = 0
                    var compressedCount = 0
                    if (localFile != null && localFile.canRead()) {
                        val files = localFile.listFiles { f -> f.isFile } ?: emptyArray()
                        for (file in files) {
                            val ext = file.extension.lowercase()
                            if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "bmp") {
                                uncompressedCount++
                            } else if (ext == "webp" || ext == "avif") {
                                compressedCount++
                            }
                        }
                    } else {
                        val files = chapter.listFiles().orEmpty()
                        for (file in files) {
                            val ext = file.extension?.lowercase().orEmpty()
                            if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "bmp") {
                                uncompressedCount++
                            } else if (ext == "webp" || ext == "avif") {
                                compressedCount++
                            }
                        }
                    }
                    val total = uncompressedCount + compressedCount
                    total > 0 && (uncompressedCount.toDouble() / total > 0.10)
                } else {
                    false
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to check eligibility for ${chapter.name}" }
                false
            }
        }
    }
}

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
    val selectedChapterUris = MutableStateFlow<Set<String>>(emptySet())

    val format = MutableStateFlow("")
    val quality = MutableStateFlow(0)
    val effort = MutableStateFlow(0)
    val autoGrayscale = MutableStateFlow<Boolean?>(null)
    val stripMetadata = MutableStateFlow<Boolean?>(null)
    val onlyWhileCharging = MutableStateFlow(false)

    fun clearCache() {
        eligibleSeries.value = null
        selectedChapterUris.value = emptySet()
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
