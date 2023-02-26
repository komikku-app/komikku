package eu.kanade.data.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.base.RaisedSearchMetadata
import tachiyomi.core.util.lang.awaitSingle
import tachiyomi.data.source.SourcePagingSource

abstract class EHentaiPagingSource(override val source: EHentai) : SourcePagingSource(source) {

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

class EHentaiSearchPagingSource(source: EHentai, val query: String, val filters: FilterList) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.fetchSearchManga(currentPage, query, filters).awaitSingle()
    }
}

class EHentaiPopularPagingSource(source: EHentai) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.fetchPopularManga(currentPage).awaitSingle()
    }
}

class EHentaiLatestPagingSource(source: EHentai) : EHentaiPagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.fetchLatestUpdates(currentPage).awaitSingle()
    }
}
