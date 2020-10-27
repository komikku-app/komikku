package exh.md.follows

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import exh.source.getMainSource

/**
 * Presenter of [MangaDexFollowsController]. Inherit BrowseCataloguePresenter.
 */
class MangaDexFollowsPresenter(sourceId: Long) : BrowseSourcePresenter(sourceId) {

    override fun createPager(query: String, filters: FilterList): Pager {
        val sourceAsMangaDex = source.getMainSource() as MangaDex
        return MangaDexFollowsPager(sourceAsMangaDex)
    }
}
