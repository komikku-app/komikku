package exh.md.follows

import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPager(val source: MangaDex) : Pager() {

    override suspend fun requestNextPage() {
        onPageReceived(source.fetchFollows(currentPage))
    }
}
