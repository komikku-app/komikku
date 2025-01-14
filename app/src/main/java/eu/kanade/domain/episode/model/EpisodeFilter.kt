package eu.kanade.domain.episode.model

import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import tachiyomi.domain.anime.model.Manga
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.episode.model.Chapter
import tachiyomi.domain.episode.service.getChapterSort
import tachiyomi.source.local.isLocal

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<Chapter>.applyFilters(
    manga: Manga,
    downloadManager: DownloadManager, /* SY --> */
    mergedManga: Map<Long, Manga>, /* SY <-- */
): List<Chapter> {
    val isLocalManga = manga.isLocal()
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter

    return filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { chapter ->
            // SY -->
            @Suppress("NAME_SHADOWING")
            val manga = mergedManga.getOrElse(chapter.mangaId) { manga }
            // SY <--
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    /* SY --> */ manga.ogTitle /* SY <-- */,
                    manga.source,
                )
                downloaded || isLocalManga
            }
        }
        .sortedWith(getChapterSort(manga))
}

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<EpisodeList.Item>.applyFilters(manga: Manga): Sequence<EpisodeList.Item> {
    val isLocalManga = manga.isLocal()
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter
    return asSequence()
        .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
        .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
}
