package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.presentation.browse.BrowseTab
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import exh.ui.smartsearch.SmartSearchController

@Composable
fun sourcesTab(
    router: Router?,
    presenter: SourcesPresenter,
) = BrowseTab(
    // SY -->
    titleRes = when (presenter.controllerMode) {
        SourcesController.Mode.CATALOGUE -> R.string.label_sources
        SourcesController.Mode.SMART_SEARCH -> R.string.find_in_another_source
    },
    actions = if (presenter.controllerMode == SourcesController.Mode.CATALOGUE) {
        listOf(
            AppBar.Action(
                title = stringResource(R.string.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { router?.pushController(GlobalSearchController()) },
            ),
            AppBar.Action(
                title = stringResource(R.string.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { router?.pushController(SourceFilterController()) },
            ),
        )
    } else emptyList(),
    // SY <--
    content = {
        SourcesScreen(
            presenter = presenter,
            onClickItem = { source ->
                // SY -->
                val controller = when {
                    presenter.controllerMode == SourcesController.Mode.SMART_SEARCH ->
                        SmartSearchController(source.id, presenter.smartSearchConfig!!)
                    presenter.useNewSourceNavigation -> SourceFeedController(source.id)
                    else -> BrowseSourceController(source)
                }
                presenter.onOpenSource(source)
                router?.pushController(controller)
                // SY <--
            },
            onClickDisable = { source ->
                presenter.toggleSource(source)
            },
            onClickLatest = { source ->
                presenter.onOpenSource(source)
                router?.pushController(LatestUpdatesController(source))
            },
            onClickPin = { source ->
                presenter.togglePin(source)
            },
            // SY -->
            onClickSetCategories = { source, categories ->
                presenter.setSourceCategories(source, categories)
            },
            onClickToggleDataSaver = { source ->
                presenter.toggleExcludeFromDataSaver(source)
            },
            // SY <--
        )
    },
)
