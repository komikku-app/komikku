package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity

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

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val onHelpClick = {
        uriHandler.openUri(LocalSource.HELP_URL)
    }

    val onWebViewClick = f@{
        val source = presenter.source as? HttpSource ?: return@f
        val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            BrowseSourceSimpleToolbar(
                title = stringResource(R.string.mangadex_follows),
                displayMode = presenter.displayMode,
                onDisplayModeChange = onDisplayModeChange,
                navigateUp = navigateUp,
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
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(MoreController.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaLongClick,
        )
    }
}
