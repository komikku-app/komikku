package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga/* SY --> */, mangaMap: Map<Long, Manga>?): List<Chapter> {
    if (manga.isLocal()) return this

    val downloadCache: DownloadCache = Injekt.get()

    // SY -->
    return filter {
        val chapterManga = mangaMap?.get(it.mangaId) ?: manga
        downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, chapterManga.ogTitle, chapterManga.source, false)
    }
    // SY <--
}
