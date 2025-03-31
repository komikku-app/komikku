package exh.ui.smartsearch

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import exh.smartsearch.SmartSearchEngine
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartSearchScreenModel(
    private val sourceId: Long,
    private val config: SourcesScreen.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<SmartSearchScreenModel.SearchResults?>(null) {
    private val smartSearchEngine = SmartSearchEngine()

    val source = sourceManager.get(sourceId) as CatalogueSource

    init {
        screenModelScope.launchIO {
            val result = try {
                val resultManga = smartSearchEngine.smartSearch(source, config.origTitle)
                if (resultManga != null) {
                    val localManga = networkToLocalManga(resultManga)
                    SearchResults.Found(localManga)
                } else {
                    SearchResults.NotFound
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                } else {
                    SearchResults.Error
                }
            }

            mutableState.value = result
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        data object NotFound : SearchResults()
        data object Error : SearchResults()
    }
}
