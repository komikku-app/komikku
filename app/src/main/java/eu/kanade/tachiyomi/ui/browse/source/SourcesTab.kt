package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourceCategoriesDialog
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen.SmartSearchConfig
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun Screen.sourcesTab(
    smartSearchConfig: SmartSearchConfig? = null,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { SourcesScreenModel(smartSearchConfig = smartSearchConfig) }
    val state by screenModel.state.collectAsState()

    return TabContent(
        // SY -->
        titleRes = when (smartSearchConfig == null) {
            true -> R.string.label_sources
            false -> R.string.find_in_another_source
        },
        actions = if (smartSearchConfig == null) {
            listOf(
                AppBar.Action(
                    title = stringResource(R.string.action_global_search),
                    icon = Icons.Outlined.TravelExplore,
                    onClick = { navigator.push(GlobalSearchScreen()) },
                ),
                AppBar.Action(
                    title = stringResource(R.string.action_filter),
                    icon = Icons.Outlined.FilterList,
                    onClick = { navigator.push(SourcesFilterScreen()) },
                ),
            )
        } else {
            emptyList()
        },
        // SY <--
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // SY -->
                    val screen = when {
                        smartSearchConfig != null -> SmartSearchScreen(source.id, smartSearchConfig)
                        listing == Listing.Popular && screenModel.useNewSourceNavigation -> SourceFeedScreen(source.id)
                        else -> BrowseSourceScreen(source.id, listing.query)
                    }
                    screenModel.onOpenSource(source)
                    navigator.push(screen)
                    // SY <--
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
            )

            when (val dialog = state.dialog) {
                is SourcesScreenModel.Dialog.SourceLongClick -> {
                    val source = dialog.source
                    SourceOptionsDialog(
                        source = source,
                        onClickPin = {
                            screenModel.togglePin(source)
                            screenModel.closeDialog()
                        },
                        onClickDisable = {
                            screenModel.toggleSource(source)
                            screenModel.closeDialog()
                        },
                        // SY -->
                        onClickSetCategories = {
                            screenModel.showSourceCategoriesDialog(source)
                        }.takeIf { state.categories.isNotEmpty() },
                        onClickToggleDataSaver = {
                            screenModel.toggleExcludeFromDataSaver(source)
                            screenModel.closeDialog()
                        }.takeIf { state.dataSaverEnabled != 0 },
                        onDismiss = screenModel::closeDialog,
                    )
                }
                is SourcesScreenModel.Dialog.SourceCategories -> {
                    val source = dialog.source
                    SourceCategoriesDialog(
                        source = source,
                        categories = state.categories,
                        onClickCategories = { categories ->
                            screenModel.setSourceCategories(source, categories)
                            screenModel.closeDialog()
                        },
                        onDismissRequest = screenModel::closeDialog,
                    )
                }
                null -> Unit
            }

            val internalErrString = stringResource(R.string.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        SourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
