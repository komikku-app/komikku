package exh.ui.smartsearch

import android.os.Bundle
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.smartsearch.SmartSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartSearchPresenter(
    private val source: CatalogueSource,
    private val config: SourcesController.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) :
    BasePresenter<SmartSearchController>() {

    private val _smartSearchFlow = MutableSharedFlow<SearchResults>()
    val smartSearchFlow = _smartSearchFlow.asSharedFlow()

    private val smartSearchEngine = SmartSearchEngine()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            val result = try {
                val resultManga = smartSearchEngine.smartSearch(source, config.origTitle)
                if (resultManga != null) {
                    val localManga = networkToLocalManga.await(resultManga)
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

            _smartSearchFlow.emit(result)
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        object NotFound : SearchResults()
        object Error : SearchResults()
    }
}
