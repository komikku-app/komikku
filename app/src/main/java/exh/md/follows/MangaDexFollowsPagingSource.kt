package exh.md.follows

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowsePagingSource

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPagingSource(val source: MangaDex) : BrowsePagingSource() {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.fetchFollows(currentPage)
    }
}
