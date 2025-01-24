package exh.recs

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.SourceCatalogue
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseRecommendsScreenModel(
    val mangaId: Long,
    sourceId: Long,
    private val recommendationSourceName: String,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(sourceId, null) {

    val manga = runBlocking { getManga.await(mangaId) }!!

    // KMK -->
    private val sourceCatalogue = SourceCatalogue(sourceId)
    // KMK <--

    val recommendationSource: RecommendationPagingSource
        get() = RecommendationPagingSource.createSources(
            manga,
            // KMK -->
            sourceCatalogue,
            // KMK <--
        ).first {
            it::class.qualifiedName == recommendationSourceName
        }

    override fun createSourcePagingSource(query: String, filters: FilterList) = recommendationSource

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
