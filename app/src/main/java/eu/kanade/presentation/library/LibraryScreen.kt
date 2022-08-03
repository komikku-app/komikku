package eu.kanade.presentation.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.components.LibraryBottomActionMenu
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.library.LibraryPresenter

@Composable
fun LibraryScreen(
    presenter: LibraryPresenter,
    onMangaClicked: (Long) -> Unit,
    onGlobalSearchClicked: () -> Unit,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: (Category?) -> Unit,
    // SY -->
    onClickCleanTitles: () -> Unit,
    onClickMigrate: () -> Unit,
    onClickAddToMangaDex: () -> Unit,
    onClickSyncExh: () -> Unit,
    onOpenReader: (LibraryManga) -> Unit,
    // SY <--
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val insets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
    Crossfade(targetState = presenter.isLoading) { state ->
        when (state) {
            true -> LoadingScreen()
            false -> Scaffold(
                modifier = Modifier
                    .windowInsetsPadding(insets)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    val title by presenter.getToolbarTitle()
                    val tabVisible = presenter.tabVisibility && presenter.categories.size > 1
                    LibraryToolbar(
                        state = presenter,
                        title = title,
                        incognitoMode = !tabVisible && presenter.isIncognitoMode,
                        downloadedOnlyMode = !tabVisible && presenter.isDownloadOnly,
                        onClickUnselectAll = onClickUnselectAll,
                        onClickSelectAll = onClickSelectAll,
                        onClickInvertSelection = onClickInvertSelection,
                        onClickFilter = onClickFilter,
                        onClickRefresh = { onClickRefresh(null) },
                        // SY -->
                        onClickSyncExh = onClickSyncExh,
                        // SY <--
                        scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
                    )
                },
                bottomBar = {
                    LibraryBottomActionMenu(
                        visible = presenter.selectionMode,
                        onChangeCategoryClicked = onChangeCategoryClicked,
                        onMarkAsReadClicked = onMarkAsReadClicked,
                        onMarkAsUnreadClicked = onMarkAsUnreadClicked,
                        onDownloadClicked = onDownloadClicked.takeIf { presenter.selection.none { it.source == LocalSource.ID } },
                        onDeleteClicked = onDeleteClicked,
                        // SY -->
                        onClickCleanTitles = onClickCleanTitles.takeIf { presenter.showCleanTitles },
                        onClickMigrate = onClickMigrate,
                        onClickAddToMangaDex = onClickAddToMangaDex.takeIf { presenter.showAddToMangadex },
                        // SY <--
                    )
                },
            ) { paddingValues ->
                LibraryContent(
                    state = presenter,
                    contentPadding = paddingValues,
                    currentPage = { presenter.activeCategory },
                    isLibraryEmpty = { presenter.loadedManga.isEmpty() },
                    showPageTabs = presenter.tabVisibility,
                    showMangaCount = presenter.mangaCountVisibility,
                    onChangeCurrentPage = { presenter.activeCategory = it },
                    onMangaClicked = onMangaClicked,
                    onToggleSelection = { presenter.toggleSelection(it) },
                    onRefresh = onClickRefresh,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                    getNumberOfMangaForCategory = { presenter.getMangaCountForCategory(it) },
                    getDisplayModeForPage = { presenter.getDisplayMode(index = it) },
                    getColumnsForOrientation = { presenter.getColumnsPreferenceForCurrentOrientation(it) },
                    getLibraryForPage = { presenter.getMangaForCategory(page = it) },
                    isIncognitoMode = presenter.isIncognitoMode,
                    isDownloadOnly = presenter.isDownloadOnly,
                    // SY -->
                    onOpenReader = onOpenReader,
                    // SY <--
                )
            }
        }
    }
}
