package exh.recs

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.recs.sources.RecommendationPagingSource
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseRecommendsScreenModel(
    val mangaId: Long,
    sourceId: Long,
    private val recommendationSourceName: String,
    private val getManga: GetManga = Injekt.get(),
    // KMK -->
    sourceManager: SourceManager = Injekt.get(),
    // KMK <--
) : BrowseSourceScreenModel(sourceId, null) {

    val manga = runBlocking { getManga.await(mangaId) }!!

    // KMK -->
    val source_ = sourceManager.get(sourceId)
        ?.let { it as CatalogueSource }
    // KMK <--

    val recommendationSource: RecommendationPagingSource
        get() = RecommendationPagingSource.createSources(
            manga,
            // KMK -->
            source_,
            // KMK <--
        ).first {
            it::class.qualifiedName == recommendationSourceName
        }

    override fun createSourcePagingSource(query: String, filters: FilterList) = recommendationSource

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
