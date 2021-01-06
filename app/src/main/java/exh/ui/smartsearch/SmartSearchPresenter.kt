package exh.ui.smartsearch

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import exh.smartsearch.SmartSearchEngine
import exh.ui.base.CoroutinePresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class SmartSearchPresenter(private val source: CatalogueSource, private val config: SourceController.SmartSearchConfig) :
    CoroutinePresenter<SmartSearchController>() {

    val smartSearchFlow = MutableSharedFlow<SearchResults>()

    private val smartSearchEngine = SmartSearchEngine()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        launch(Dispatchers.IO) {
            val result = try {
                val resultManga = smartSearchEngine.smartSearch(source, config.origTitle)
                if (resultManga != null) {
                    val localManga = smartSearchEngine.networkToLocalManga(resultManga, source.id)
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

            smartSearchFlow.emit(result)
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        object NotFound : SearchResults()
        object Error : SearchResults()
    }
}
