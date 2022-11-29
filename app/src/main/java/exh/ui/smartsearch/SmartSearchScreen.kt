package exh.ui.smartsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.toast

class SmartSearchScreen(private val sourceId: Long, private val smartSearchConfig: SourcesController.SmartSearchConfig) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SmartSearchScreenModel(sourceId, smartSearchConfig) }

        val router = LocalRouter.currentOrThrow
        val context = LocalContext.current
        val state by screenModel.state.collectAsState()
        LaunchedEffect(state) {
            val results = state
            if (results != null) {
                if (results is SmartSearchScreenModel.SearchResults.Found) {
                    val transaction = MangaController(results.manga.id, true, smartSearchConfig).withFadeTransaction()
                    router.replaceTopController(transaction)
                } else {
                    if (results is SmartSearchScreenModel.SearchResults.NotFound) {
                        context.toast(R.string.could_not_find_entry)
                    } else {
                        context.toast(R.string.automatic_search_error)
                    }
                    val transaction = BrowseSourceController(
                        screenModel.source,
                        smartSearchConfig.origTitle,
                        smartSearchConfig,
                    ).withFadeTransaction()
                    router.replaceTopController(transaction)
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = screenModel.source.name,
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
}
