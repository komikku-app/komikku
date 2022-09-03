package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter

@Composable
fun BrowseRecommendationsScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    title: String,
    onMangaClick: (Manga) -> Unit,
) {
    val columns by presenter.getColumnsPreferenceForCurrentOrientation()

    Scaffold(
        topBar = { scrollBehavior ->
            BrowseSourceSimpleToolbar(
                navigateUp = navigateUp,
                title = title,
                displayMode = presenter.displayMode,
                onDisplayModeChange = { presenter.displayMode = it },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        BrowseSourceContent(
            state = presenter,
            mangaList = presenter.getMangaList().collectAsLazyPagingItems(),
            getMangaState = { presenter.getManga(it) },
            // SY -->
            getMetadataState = { manga, metadata ->
                presenter.getRaisedSearchMetadata(manga, metadata)
            },
            // SY <--
            columns = columns,
            // SY -->
            ehentaiBrowseDisplayMode = false,
            // SY <--
            displayMode = presenter.displayMode,
            snackbarHostState = remember { SnackbarHostState() },
            contentPadding = paddingValues,
            onWebViewClick = null,
            onHelpClick = null,
            onLocalSourceHelpClick = null,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaClick,
        )
    }
}
