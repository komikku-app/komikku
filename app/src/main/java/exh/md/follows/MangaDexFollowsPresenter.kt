package exh.md.follows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.getMainSource

/**
 * Presenter of [MangaDexFollowsController]. Inherit BrowseCataloguePresenter.
 */
class MangaDexFollowsPresenter(sourceId: Long) : BrowseSourcePresenter(sourceId) {

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return MangaDexFollowsPagingSource(source!!.getMainSource() as MangaDex)
    }

    @Composable
    override fun getRaisedSearchMetadata(manga: Manga, initialMetadata: RaisedSearchMetadata?): State<RaisedSearchMetadata?> {
        return remember { mutableStateOf(initialMetadata) }
    }
}
