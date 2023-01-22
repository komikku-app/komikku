package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterItem
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    return applyFilters(manga, downloadManager).let { chapters ->
        // SY -->
        if (manga.isEhBasedManga()) {
            return@let if (manga.sortDescending()) {
                chapters.firstOrNull()?.takeUnless { it.read }
            } else {
                chapters.lastOrNull()?.takeUnless { it.read }
            }
        }
        // SY <--
        if (manga.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterItem>.getNextUnread(manga: Manga): Chapter? {
    return applyFilters(manga).let { chapters ->
        // SY -->
        if (manga.isEhBasedManga()) {
            return@let if (manga.sortDescending()) {
                chapters.firstOrNull()?.takeUnless { it.chapter.read }
            } else {
                chapters.lastOrNull()?.takeUnless { it.chapter.read }
            }
        }
        // SY <--
        if (manga.sortDescending()) {
            chapters.findLast { !it.chapter.read }
        } else {
            chapters.find { !it.chapter.read }
        }
    }?.chapter
}
