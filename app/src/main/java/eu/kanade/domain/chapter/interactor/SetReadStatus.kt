package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.download.interactor.DeleteDownload
import exh.source.MERGED_SOURCE_ID
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class SetReadStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
) {

    private val mapper = { chapter: Chapter, read: Boolean ->
        ChapterUpdate(
            read = read,
            lastPageRead = if (!read) 0 else null,
            id = chapter.id,
        )
    }

    /**
     * Mark chapters as read/unread, also delete downloaded chapters if 'After manually marked as read' is set.
     *
     * KMK: links are fully qualified (no imports) so this file also compiles for desktop, where the
     * referenced screen models do not exist (app-shared borrowed-app-sources.gradle.kts, desktopCopy).
     *
     * Called from:
     *  - [eu.kanade.tachiyomi.ui.library.LibraryScreenModel]: Manually select mangas & mark as read
     *  - [eu.kanade.tachiyomi.ui.manga.MangaScreenModel.markChaptersRead]: Manually select chapters & mark as read or swipe chapter as read
     *  - [eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.markUpdatesRead]: Manually select chapters & mark as read
     *  - [eu.kanade.tachiyomi.data.library.LibraryUpdateJob.updateChapterList]: when a manga is updated and has new chapter but already read,
     *  it will mark that new **duplicated** chapter as read & delete downloading/downloaded -> should be treat as
     *  automatically ~ no auto delete
     *  - [eu.kanade.tachiyomi.ui.reader.ReaderViewModel.updateChapterProgress]: mark **duplicated** chapter as read after finish reading -> should be
     *  treated as not manually mark as read so not auto-delete (there are cases where chapter number is mistaken by volume number)
     */
    suspend fun await(
        read: Boolean,
        vararg chapters: Chapter,
        // KMK -->
        manually: Boolean = true,
        // KMK <--
    ): Result = withNonCancellableContext {
        val chaptersToUpdate = chapters.filter {
            when (read) {
                true -> !it.read
                false -> it.read || it.lastPageRead > 0
            }
        }
        if (chaptersToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoChapters
        }

        try {
            chapterRepository.updateAll(
                chaptersToUpdate.map { mapper(it, read) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        if (
            // KMK -->
            manually &&
            // KMK <--
            read &&
            downloadPreferences.removeAfterMarkedAsRead().get()
        ) {
            chaptersToUpdate
                // KMK -->
                .map { it.copy(read = true) } // mark as read so it will respect category exclusion
                // KMK <--
                .groupBy { it.mangaId }
                .forEach { (mangaId, chapters) ->
                    deleteDownload.awaitAll(
                        manga = mangaRepository.getMangaById(mangaId),
                        chapters = chapters.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    suspend fun await(mangaId: Long, read: Boolean): Result = withNonCancellableContext {
        await(
            read = read,
            chapters = chapterRepository
                .getChapterByMangaId(mangaId)
                .toTypedArray(),
        )
    }

    // SY -->
    private suspend fun awaitMerged(mangaId: Long, read: Boolean) = withNonCancellableContext f@{
        return@f await(
            read = read,
            chapters = getMergedChaptersByMangaId
                .await(mangaId, dedupe = false)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, read: Boolean) = if (manga.source == MERGED_SOURCE_ID) {
        awaitMerged(manga.id, read)
    } else {
        await(manga.id, read)
    }
    // SY <--

    sealed interface Result {
        data object Success : Result
        data object NoChapters : Result
        data class InternalError(val error: Throwable) : Result
    }
}
