package exh.md.similar

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController

class MangaDexSimilarScreen(val mangaId: Long, val sourceId: Long) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { MangaDexSimilarScreenModel(mangaId, sourceId) }
        val state by screenModel.state.collectAsState()
        val router = LocalRouter.currentOrThrow
        val navigator = LocalNavigator.currentOrThrow

        val onMangaClick: (Manga) -> Unit = {
            router.pushController(MangaController(it.id, true))
        }

        val snackbarHostState = remember { SnackbarHostState() }

        val navigateUp: () -> Unit = {
            when {
                navigator.canPop -> navigator.pop()
                router.backstackSize > 1 -> router.popCurrentController()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                BrowseSourceSimpleToolbar(
                    navigateUp = navigateUp,
                    title = stringResource(R.string.similar, screenModel.manga.title),
                    displayMode = screenModel.displayMode,
                    onDisplayModeChange = { screenModel.displayMode = it },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val mangaList = remember(state.currentFilter) {
                screenModel.getMangaListFlow(state.currentFilter)
            }.collectAsLazyPagingItems()

            BrowseSourceContent(
                source = screenModel.source,
                mangaList = mangaList,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = false,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaClick,
            )
        }
    }
}
