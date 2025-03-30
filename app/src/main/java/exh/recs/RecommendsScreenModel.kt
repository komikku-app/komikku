package exh.recs

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetAnime
import tachiyomi.domain.source.repository.SourcePagingSourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RecommendsScreenModel(
    val mangaId: Long,
    sourceId: Long,
    private val getAnime: GetAnime = Injekt.get(),
) : BrowseSourceScreenModel(sourceId, null) {

    val manga = runBlocking { getAnime.await(mangaId) }!!

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return RecommendsPagingSource(source as CatalogueSource, manga)
    }
}
