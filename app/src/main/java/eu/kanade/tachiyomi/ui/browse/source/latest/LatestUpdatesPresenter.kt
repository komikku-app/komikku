package eu.kanade.tachiyomi.ui.browse.source.latest

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import exh.metadata.metadata.base.RaisedSearchMetadata

class LatestUpdatesPresenter(sourceId: Long) : BrowseSourcePresenter(sourceId) {

    override fun createPager(query: String, filters: FilterList): PagingSource<Long, /*SY --> */ Pair<SManga, RaisedSearchMetadata?> /*SY <-- */> {
        return LatestUpdatesBrowsePagingSource(source!!)
    }
}
