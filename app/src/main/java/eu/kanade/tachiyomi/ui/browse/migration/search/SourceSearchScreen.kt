package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceFloatingActionButton
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.Constants

data class SourceSearchScreen(
    private val oldManga: Manga,
    private val sourceId: Long,
    private val query: String?,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { BrowseSourceScreenModel(sourceId, query) }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.toolbarQuery ?: "",
                    onChangeSearchQuery = screenModel::setToolbarQuery,
                    onClickCloseSearch = navigator::pop,
                    onSearch = { screenModel.search(it) },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                // SY -->
                BrowseSourceFloatingActionButton(
                    isVisible = state.filters.isNotEmpty(),
                    onFabClick = screenModel::openFilterSheet,
                )
                // SY <--
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.mangaPagerFlowFlow.collectAsState()
            val openMigrateDialog: (Manga) -> Unit = {
                // SY -->
                navigator.items
                    .filterIsInstance<MigrationListScreen>()
                    .last()
                    .newSelectedItem = oldManga.id to it.id
                navigator.popUntil { it is MigrationListScreen }
                // SY <--
            }
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = screenModel.ehentaiBrowseDisplayMode,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = screenModel.source as? HttpSource ?: return@BrowseSourceContent
                    val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
                    context.startActivity(intent)
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) },
                onMangaClick = openMigrateDialog,
                onMangaLongClick = { navigator.push(MangaScreen(it.id, true)) },
            )
        }

        LaunchedEffect(state.filters) {
            screenModel.initFilterSheet(context, navigator)
        }
    }
}
