package exh.recs

import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
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

    override fun createPager(query: String, filters: FilterList): Pager {
        this.manga = runBlocking { getManga.await(mangaId) }
        return RecommendsPager(manga!!)
    }
}
