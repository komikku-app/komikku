package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.metadata.metadata.base.RaisedSearchMetadata

abstract class BrowsePagingSource : PagingSource<Long, /*SY --> */ Pair<SManga, RaisedSearchMetadata?> /*SY <-- */>() {

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, /*SY --> */ Pair<SManga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        val mangasPage = try {
            withIOContext {
                requestNextPage(page.toInt())
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
        // SY -->
        val metadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else emptyList()
        // SY <--

        return LoadResult.Page(
            data = mangasPage.mangas
                // SY -->
                .mapIndexed { index, sManga -> sManga to metadata.getOrNull(index) },
            // SY <--
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Long, Pair<SManga, RaisedSearchMetadata?>>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
