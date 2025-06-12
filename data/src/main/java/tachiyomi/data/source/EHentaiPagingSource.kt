package tachiyomi.data.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.metadata.metadata.RaisedSearchMetadata
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.manga.model.Manga

abstract class EHentaiPagingSource(
    source: CatalogueSource,
) : BaseSourcePagingSource(source) {

    override suspend fun getPageLoadResult(
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, Pair<Manga, RaisedSearchMetadata?>> {
        mangasPage as MetadataMangasPage
        val metadata = mangasPage.mangasMetadata

        val manga = mangasPage.mangas
            .mapIndexed { index, sManga -> sManga.toDomainManga(source.id) to metadata.getOrNull(index) }
            .filter { seenManga.add(it.first.url) }
            // KMK -->
            .let { pairs -> networkToLocalManga(pairs.map { it.first }).zip(pairs.map { it.second }) }
        // KMK <--

        return LoadResult.Page(
            data = manga,
            prevKey = null,
            nextKey = mangasPage.nextKey,
        )
    }
}

class EHentaiSearchPagingSource(
    source: CatalogueSource,
    val query: String,
    val filters: FilterList,
) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query.sanitize(), filters)
    }
}

class EHentaiPopularPagingSource(source: CatalogueSource) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class EHentaiLatestPagingSource(source: CatalogueSource) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}
