package exh.md.similar

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.paging.PagingSource
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [MangaDexSimilarController]. Inherit BrowseCataloguePresenter.
 */
class MangaDexSimilarPresenter(
    val mangaId: Long,
    sourceId: Long,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourcePresenter(sourceId) {

    var manga: Manga? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        this.manga = runBlocking { getManga.await(mangaId) }
    }

    override fun createPager(query: String, filters: FilterList): PagingSource<Long, Pair<SManga, RaisedSearchMetadata?>> {
        return MangaDexSimilarPagingSource(manga!!, source!!.getMainSource() as MangaDex)
    }

    @Composable
    override fun getRaisedSearchMetadata(manga: Manga, initialMetadata: RaisedSearchMetadata?): State<RaisedSearchMetadata?> {
        return remember { mutableStateOf(initialMetadata) }
    }
}
