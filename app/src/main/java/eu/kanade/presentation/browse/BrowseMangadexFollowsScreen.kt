package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode

@Composable
fun BrowseMangadexFollowsScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val columns by presenter.getColumnsPreferenceForCurrentOrientation()

    val mangaList = presenter.getMangaList().collectAsLazyPagingItems()

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            BrowseSourceSimpleToolbar(
                title = stringResource(R.string.mangadex_follows),
                displayMode = presenter.displayMode,
                onDisplayModeChange = onDisplayModeChange,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { paddingValues ->
        BrowseSourceContent(
            source = presenter.source,
            mangaList = mangaList,
            getMangaState = { presenter.getManga(it) },
            // SY -->
            getMetadataState = { manga, metadata ->
                presenter.getRaisedSearchMetadata(manga, metadata)
            },
            // SY <--
            columns = columns,
            // SY -->
            ehentaiBrowseDisplayMode = presenter.ehentaiBrowseDisplayMode,
            // SY <--
            displayMode = presenter.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = null,
            onHelpClick = null,
            onLocalSourceHelpClick = null,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaLongClick,
        )
    }
}
