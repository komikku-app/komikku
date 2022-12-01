package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

class BrowseSourceController(bundle: Bundle) : BasicFullComposeController(bundle) {

    constructor(
        sourceId: Long,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, sourceId)
            if (query != null) {
                putString(SEARCH_QUERY_KEY, query)
            }

            // SY -->
            if (smartSearchConfig != null) {
                putSerializable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }

            if (savedSearch != null) {
                putLong(SAVED_SEARCH_CONFIG_KEY, savedSearch)
            }

            if (filterList != null) {
                putString(FILTERS_CONFIG_KEY, filterList)
            }
            // SY <--
        },
    )

    constructor(
        source: CatalogueSource,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    constructor(
        source: Source,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    private val sourceId = args.getLong(SOURCE_ID_KEY)
    private val initialQuery = args.getString(SEARCH_QUERY_KEY)

    // SY -->
    private val filtersJson = args.getString(FILTERS_CONFIG_KEY)
    private val savedSearch = args.getLong(SAVED_SEARCH_CONFIG_KEY, 0).takeUnless { it == 0L }
    // SY <--

    private val queryEvent = Channel<BrowseSourceScreen.SearchType>()

    @Composable
    override fun ComposeContent() {
        Navigator(
            screen = BrowseSourceScreen(
                sourceId = sourceId,
                query = initialQuery,
                // SY -->
                filtersJson = filtersJson,
                savedSearch = savedSearch,
                // SY <--
            ),
        ) { navigator ->
            CurrentScreen()

            LaunchedEffect(Unit) {
                queryEvent.consumeAsFlow()
                    .collectLatest {
                        val screen = (navigator.lastItem as? BrowseSourceScreen)
                        when (it) {
                            is BrowseSourceScreen.SearchType.Genre -> screen?.searchGenre(it.txt)
                            is BrowseSourceScreen.SearchType.Text -> screen?.search(it.txt)
                        }
                    }
            }
        }
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        viewScope.launch { queryEvent.send(BrowseSourceScreen.SearchType.Text(newQuery)) }
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        viewScope.launch { queryEvent.send(BrowseSourceScreen.SearchType.Genre(genreName)) }
    }
}

private const val SOURCE_ID_KEY = "sourceId"
private const val SEARCH_QUERY_KEY = "searchQuery"

// SY -->
private const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
private const val SAVED_SEARCH_CONFIG_KEY = "savedSearch"
private const val FILTERS_CONFIG_KEY = "filters"
// SY <--
