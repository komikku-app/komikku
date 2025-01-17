package tachiyomi.data.source

import eu.kanade.tachiyomi.animesource.CatalogueSource
import eu.kanade.tachiyomi.animesource.model.FilterList
import eu.kanade.tachiyomi.animesource.model.MangasPage
import eu.kanade.tachiyomi.animesource.model.MetadataMangasPage
import eu.kanade.tachiyomi.animesource.model.SManga
import exh.metadata.metadata.RaisedSearchMetadata

abstract class EHentaiPagingSource(override val source: CatalogueSource) : SourcePagingSource(source) {

    override fun getPageLoadResult(
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, Pair<SManga, RaisedSearchMetadata?>> {
        mangasPage as MetadataMangasPage
        val metadata = mangasPage.mangasMetadata

        return LoadResult.Page(
            data = mangasPage.mangas
                .mapIndexed { index, sManga -> sManga to metadata.getOrNull(index) },
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
        return source.getSearchManga(currentPage, query, filters)
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
