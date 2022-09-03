package exh.recs

import android.os.Bundle
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RecommendsController]. Inherit BrowseCataloguePresenter.
 */
class RecommendsPresenter(
    val mangaId: Long,
    sourceId: Long,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourcePresenter(sourceId) {

    var manga: Manga? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        this.manga = runBlocking { getManga.await(mangaId) }
    }

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return RecommendsPagingSource(source!!, manga!!)
    }
}
