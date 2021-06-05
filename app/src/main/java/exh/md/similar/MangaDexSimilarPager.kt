package exh.md.similar

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager

/**
 * MangaDexSimilarPager inherited from the general Pager.
 */
class MangaDexSimilarPager(val manga: Manga, val source: MangaDex) : Pager() {

    override suspend fun requestNextPage() {
        val mangasPage = source.getMangaSimilar(manga.toMangaInfo())

        if (mangasPage.mangas.isNotEmpty()) {
            onPageReceived(mangasPage)
        } else {
            throw NoResultsException()
        }
    }
}
