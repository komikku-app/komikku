package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataAnimesPage
import eu.kanade.tachiyomi.source.model.SAnime
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.repository.SourcePagingSourceType

class SourceSearchPagingSource(source: CatalogueSource, val query: String, val filters: FilterList) :
    SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class SourcePagingSource(
    protected open val source: CatalogueSource,
) : SourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, /*SY --> */ Pair<SAnime, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        val mangasPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoResultsException()
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        // SY -->
        return getPageLoadResult(params, mangasPage)
        // SY <--
    }

    // SY -->
    open fun getPageLoadResult(
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, /*SY --> */ Pair<SAnime, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        // SY -->
        val metadata = if (mangasPage is MetadataAnimesPage) {
            mangasPage.animesMetadata
        } else {
            emptyList()
        }
        // SY <--

        return LoadResult.Page(
            data = mangasPage.animes
                // SY -->
                .mapIndexed { index, sManga -> sManga to metadata.getOrNull(index) },
            // SY <--
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, /*SY --> */ Pair<SAnime, RaisedSearchMetadata?>/*SY <-- */>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
