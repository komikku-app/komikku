package exh.md.similar

import eu.kanade.domain.anime.model.toSManga
import eu.kanade.tachiyomi.animesource.model.MangasPage
import eu.kanade.tachiyomi.animesource.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tachiyomi.data.source.NoResultsException
import tachiyomi.data.source.SourcePagingSource
import tachiyomi.domain.anime.model.Manga

/**
 * MangaDexSimilarPagingSource inherited from the general Pager.
 */
class MangaDexSimilarPagingSource(val manga: Manga, val mangadex: MangaDex) : SourcePagingSource(mangadex) {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val mangasPage = coroutineScope {
            val similarPageDef = async { mangadex.getMangaSimilar(manga.toSManga()) }
            val relatedPageDef = async { mangadex.getMangaRelated(manga.toSManga()) }
            val similarPage = similarPageDef.await()
            val relatedPage = relatedPageDef.await()

            MetadataMangasPage(
                relatedPage.mangas + similarPage.mangas,
                false,
                relatedPage.mangasMetadata + similarPage.mangasMetadata,
            )
        }

        return mangasPage.takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
