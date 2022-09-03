package exh.md.follows

import eu.kanade.data.source.SourcePagingSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPagingSource(val mangadex: MangaDex) : SourcePagingSource(mangadex) {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return mangadex.fetchFollows(currentPage)
    }
}
