package exh.recs

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.SourceCatalogue
import exh.recs.sources.StaticResultPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseRecommendsScreenModel(
    private val args: BrowseRecommendsScreen.Args,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(
    sourceId = when (args) {
        is BrowseRecommendsScreen.Args.SingleSourceManga -> args.sourceId
        is BrowseRecommendsScreen.Args.MergedSourceMangas -> args.results.recAssociatedSourceId ?: RECOMMENDS_SOURCE
    },
    listingQuery = null,
) {
    private var manga: Manga? = null

    init {
        screenModelScope.launchIO {
            manga = when (args) {
                is BrowseRecommendsScreen.Args.SingleSourceManga -> getManga.await(args.mangaId)
                is BrowseRecommendsScreen.Args.MergedSourceMangas -> null
            }
        }
    }

    val recommendationSource: RecommendationPagingSource
        get() = when (args) {
            is BrowseRecommendsScreen.Args.MergedSourceMangas -> StaticResultPagingSource(args.results)
            is BrowseRecommendsScreen.Args.SingleSourceManga -> RecommendationPagingSource.createSources(
                manga ?: runBlocking(Dispatchers.IO) { getManga.await(args.mangaId)!! },
                // KMK -->
                SourceCatalogue(sourceId),
                // KMK <--
            ).first {
                it::class.qualifiedName == args.recommendationSourceName
            }
        }

    override fun createSourcePagingSource(query: String, filters: FilterList) = recommendationSource

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        // Overridden to prevent our custom metadata from being replaced from a cache
        return flatMapLatest { manga -> flowOf(manga to metadata) }
    }

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
