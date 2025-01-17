package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Page
import eu.kanade.tachiyomi.util.storage.DiskUtil
import exh.log.xLogE
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Manga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to manage episode downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new episodes or query
 * downloaded episodes.
 */
@OptIn(DelicateCoroutinesApi::class)
class DownloadManager(
    private val context: Context,
    private val provider: DownloadProvider = Injekt.get(),
    private val cache: DownloadCache = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    /**
     * Downloader whose only task is to download episodes.
     */
    private val downloader = Downloader(context, provider, cache)

    val isRunning: Boolean
        get() = downloader.isRunning

    /**
     * Queue to delay the deletion of a list of episodes until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    val queueState
        get() = downloader.queueState

    // For use by DownloadService only
    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = DownloadJob.isRunningFlow(context)

    /**
     * Tells the downloader to begin downloads.
     */
    fun startDownloads() {
        if (downloader.isRunning) return

        if (DownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            DownloadJob.start(context)
        }
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    /**
     * Empties the download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    /**
     * Returns the download from queue if the episode is queued for download
     * else it will return null which means that the episode is not queued for download
     *
     * @param chapterId the episode to check.
     */
    fun getQueuedDownloadOrNull(chapterId: Long): Download? {
        return queueState.value.find { it.episode.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long) {
        val existingDownload = getQueuedDownloadOrNull(chapterId)
        // If not in queue try to start a new download
        val toAdd = existingDownload ?: runBlocking { Download.fromChapterId(chapterId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        downloader.updateQueue(downloads)
    }

    /**
     * Tells the downloader to enqueue the given list of episodes.
     *
     * @param manga the manga of the episodes.
     * @param episodes the list of episodes to enqueue.
     * @param autoStart whether to start the downloader after enqueing the episodes.
     */
    fun downloadChapters(manga: Manga, episodes: List<Episode>, autoStart: Boolean = true) {
        downloader.queueChapters(manga, episodes, autoStart)
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!DownloadJob.isRunning(context)) startDownloads()
    }

    /**
     * Builds the page list of a downloaded episode.
     *
     * @param source the source of the episode.
     * @param manga the manga of the episode.
     * @param episode the downloaded episode.
     * @return the list of pages from the episode.
     */
    fun buildPageList(source: AnimeSource, manga: Manga, episode: Episode): List<Page> {
        val chapterDir = provider.findChapterDir(
            episode.name,
            episode.scanlator,
            /* SY --> */ manga.ogTitle /* SY <-- */,
            source,
        )
        val files = chapterDir?.listFiles().orEmpty()
            .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.page_list_empty_error))
        }

        return files.sortedBy { it.name }
            .mapIndexed { i, file ->
                Page(i, uri = file.uri).apply { status = Page.State.READY }
            }
    }

    /**
     * Returns true if the episode is downloaded.
     *
     * @param chapterName the name of the episode to query.
     * @param chapterScanlator scanlator of the episode to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the episode.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(chapterName, chapterScanlator, mangaTitle, sourceId, skipCache)
    }

    /**
     * Returns the amount of downloaded episodes.
     */
    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    /**
     * Returns the amount of downloaded episodes for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return cache.getDownloadCount(manga)
    }

    fun cancelQueuedDownloads(downloads: List<Download>) {
        removeFromDownloadQueue(downloads.map { it.episode })
    }

    /**
     * Deletes the directories of a list of downloaded episodes.
     *
     * @param episodes the list of episodes to delete.
     * @param manga the manga of the episodes.
     * @param source the source of the episodes.
     */
    fun deleteChapters(
        episodes: List<Episode>,
        manga: Manga,
        source: AnimeSource,
        // KMK -->
        /** Ignore categories exclusion */
        ignoreCategoryExclusion: Boolean = false,
        // KMK <--
    ) {
        launchIO {
            val filteredChapters = getChaptersToDelete(
                episodes,
                manga,
                // KMK -->
                ignoreCategoryExclusion,
                // KMK <--
            )
            if (filteredChapters.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredChapters)

            val (mangaDir, chapterDirs) = provider.findChapterDirs(filteredChapters, manga, source)
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, manga)

            // Delete manga directory if empty
            if (mangaDir?.listFiles()?.isEmpty() == true) {
                deleteManga(manga, source, removeQueued = false)
            }
        }
    }

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     * @param removeQueued whether to also remove queued downloads.
     */
    fun deleteManga(manga: Manga, source: AnimeSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(manga)
            }
            provider.findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)?.delete()
            cache.removeManga(manga)

            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(episodes: List<Episode>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(episodes)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    // SY -->
    /**
     * return the list of all manga folders
     */
    fun getMangaFolders(source: AnimeSource): List<UniFile> {
        return provider.findSourceDir(source)?.listFiles()?.toList().orEmpty()
    }

    /**
     * Deletes the directories of episodes that were read or have no match
     *
     * @param allEpisodes the list of episodes to delete.
     * @param manga the manga of the episodes.
     * @param source the source of the episodes.
     */
    suspend fun cleanupChapters(
        allEpisodes: List<Episode>,
        manga: Manga,
        source: AnimeSource,
        removeRead: Boolean,
        removeNonFavorite: Boolean,
    ): Int {
        var cleaned = 0

        if (removeNonFavorite && !manga.favorite) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)
            cleaned += 1 + mangaFolder.listFiles().orEmpty().size
            mangaFolder.delete()
            cache.removeManga(manga)
            return cleaned
        }

        val filesWithNoChapter = provider.findUnmatchedChapterDirs(allEpisodes, manga, source)
        cleaned += filesWithNoChapter.size
        cache.removeFolders(filesWithNoChapter.mapNotNull { it.name }, manga)
        filesWithNoChapter.forEach { it.delete() }

        if (removeRead) {
            val readChapters = allEpisodes.filter { it.read }
            val readChapterDirs = provider.findChapterDirs(readChapters, manga, source)
            readChapterDirs.second.forEach { it.delete() }
            cleaned += readChapterDirs.second.size
            cache.removeChapters(readChapters, manga)
        }

        if (cache.getDownloadCount(manga) == 0) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)
            if (!mangaFolder.listFiles().isNullOrEmpty()) {
                mangaFolder.delete()
                cache.removeManga(manga)
            } else {
                xLogE("Cache and download folder doesn't match for " + /* SY --> */ manga.ogTitle /* SY <-- */)
            }
        }
        return cleaned
    }
    // SY <--

    /**
     * Adds a list of episodes to be deleted later.
     *
     * @param episodes the list of episodes to delete.
     * @param manga the manga of the episodes.
     */
    suspend fun enqueueChaptersToDelete(episodes: List<Episode>, manga: Manga) {
        pendingDeleter.addChapters(getChaptersToDelete(episodes, manga), manga)
    }

    /**
     * Triggers the execution of the deletion of pending episodes.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames source download folder
     *
     * @param oldSource the old source.
     * @param newSource the new source.
     */
    fun renameSource(oldSource: AnimeSource, newSource: AnimeSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + Downloader.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (!oldFolder.renameTo(newName)) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames an already downloaded episode
     *
     * @param source the source of the manga.
     * @param manga the manga of the episode.
     * @param oldEpisode the existing episode with the old name.
     * @param newEpisode the target episode with the new name.
     */
    suspend fun renameChapter(source: AnimeSource, manga: Manga, oldEpisode: Episode, newEpisode: Episode) {
        val oldNames = provider.getValidChapterDirNames(oldEpisode.name, oldEpisode.scanlator)
        val mangaDir = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)

        // Assume there's only 1 version of the episode name formats present
        val oldDownload = oldNames.asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull() ?: return

        var newName = provider.getChapterDirName(newEpisode.name, newEpisode.scanlator)
        if (oldDownload.isFile && oldDownload.extension == "cbz") {
            newName += ".cbz"
        }

        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldEpisode, manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded episode: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getChaptersToDelete(
        episodes: List<Episode>,
        manga: Manga,
        // KMK -->
        /** Ignore categories exclusion */
        ignoreCategoryExclusion: Boolean = false,
        // KMK <--
    ): List<Episode> {
        // KMK -->
        val filteredCategoryManga = if (ignoreCategoryExclusion) {
            episodes
        } else {
            // KMK <--
            // Retrieve the categories that are set to exclude from being deleted on read
            val categoriesToExclude = downloadPreferences.removeExcludeCategories().get().map(String::toLong).toSet()

            val categoriesForManga = getCategories.await(manga.id)
                .map { it.id }
                .ifEmpty { listOf(0) }
            if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) {
                episodes.filterNot { it.read }
            } else {
                episodes
            }
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get() &&
            // KMK -->
            // if manually deleting single episode then will allow deleting bookmark episode
            (episodes.size > 1 || !ignoreCategoryExclusion)
            // KMK <--
        ) {
            filteredCategoryManga.filterNot { it.bookmark }
        } else {
            filteredCategoryManga
        }
    }

    fun statusFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<Download> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == Download.State.DOWNLOADING }
                    .asFlow(),
            )
        }

    fun renameMangaDir(oldTitle: String, newTitle: String, source: Long) {
        val sourceDir = provider.findSourceDir(sourceManager.getOrStub(source)) ?: return
        val mangaDir = sourceDir.findFile(DiskUtil.buildValidFilename(oldTitle)) ?: return
        mangaDir.renameTo(DiskUtil.buildValidFilename(newTitle))
    }
}
