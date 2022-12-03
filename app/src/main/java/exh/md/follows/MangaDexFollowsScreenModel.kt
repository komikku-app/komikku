package exh.md.follows

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MangaDexFollowsScreenModel(sourceId: Long) : BrowseSourceScreenModel(sourceId, null) {

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return MangaDexFollowsPagingSource(source.getMainSource() as MangaDex)
    }

    override fun Flow<Manga>.combineMetadata(dbManga: Manga, metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        return map { it to metadata }
    }

    override fun initFilterSheet(context: Context, navigator: Navigator) {
        // No-op: we don't allow filtering in recs
    }
}
