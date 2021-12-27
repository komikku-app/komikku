package exh.md.similar

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * MangaDexSimilarPager inherited from the general Pager.
 */
class MangaDexSimilarPager(val manga: Manga, val source: MangaDex) : Pager() {

    override suspend fun requestNextPage() {
        val mangasPage = coroutineScope {
            val similarPageDef = async { source.getMangaSimilar(manga.toMangaInfo()) }
            val relatedPageDef = async { source.getMangaRelated(manga.toMangaInfo()) }
            val similarPage = similarPageDef.await()
            val relatedPage = relatedPageDef.await()

            MetadataMangasPage(
                relatedPage.mangas + similarPage.mangas,
                false,
                relatedPage.mangasMetadata + similarPage.mangasMetadata
            )
        }

        if (mangasPage.mangas.isNotEmpty()) {
            onPageReceived(mangasPage)
        } else {
            throw NoResultsException()
        }
    }
}
