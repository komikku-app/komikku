package exh.ui.smartsearch

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.getParcelableCompat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

class SmartSearchController(bundle: Bundle) : FullComposeController<SmartSearchPresenter>() {
    private val sourceManager: SourceManager by injectLazy()

    private val source = sourceManager.get(bundle.getLong(ARG_SOURCE_ID, -1)) as CatalogueSource
    private val smartSearchConfig = bundle.getParcelableCompat<SourcesController.SmartSearchConfig>(ARG_SMART_SEARCH_CONFIG)!!

    constructor(sourceId: Long, smartSearchConfig: SourcesController.SmartSearchConfig) : this(
        bundleOf(
            ARG_SOURCE_ID to sourceId,
            ARG_SMART_SEARCH_CONFIG to smartSearchConfig,
        ),
    )

    override fun getTitle() = source.name

    override fun createPresenter() = SmartSearchPresenter(source, smartSearchConfig)

    @Composable
    override fun ComposeContent() {
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = source.name,
                    navigateUp = router::popCurrentController,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.searching_source),
                    style = MaterialTheme.typography.titleLarge,
                )
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            }
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        presenter.smartSearchFlow
            .onEach { results ->
                if (results is SmartSearchPresenter.SearchResults.Found) {
                    val transaction = MangaController(results.manga.id, true, smartSearchConfig).withFadeTransaction()
                    router.replaceTopController(transaction)
                } else {
                    if (results is SmartSearchPresenter.SearchResults.NotFound) {
                        applicationContext?.toast(R.string.could_not_find_manga)
                    } else {
                        applicationContext?.toast(R.string.automatic_search_error)
                    }
                    val transaction = BrowseSourceController(
                        source,
                        smartSearchConfig.origTitle,
                        smartSearchConfig,
                    ).withFadeTransaction()
                    router.replaceTopController(transaction)
                }
            }
            .launchIn(viewScope)
    }

    companion object {
        private const val ARG_SOURCE_ID = "SOURCE_ID"
        private const val ARG_SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
    }
}
