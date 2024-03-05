package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.library.LibraryScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TabbedScreen(
    titleRes: StringResource,
    tabs: ImmutableList<TabContent>,
    startIndex: Int? = null,
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    // KMK -->
    feedScreenModel: FeedScreenModel,
    libraryScreenModel: LibraryScreenModel,
    // KMK <--
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberPagerState { tabs.size }
    val snackbarHostState = remember { SnackbarHostState() }

    // KMK -->
    val feedScreenState by feedScreenModel.state.collectAsState()
    // KMK <--

    LaunchedEffect(startIndex) {
        if (startIndex != null) {
            state.scrollToPage(startIndex)
        }
    }

    Scaffold(
        topBar = {
            val tab = tabs[state.currentPage]
            val searchEnabled = tab.searchEnabled
            // KMK -->
            if (feedScreenState.selection.isNotEmpty())
                FeedSelectionToolbar(
                    selectedCount = feedScreenState.selection.size,
                    onClickClearSelection = feedScreenModel::clearSelection,
                    actions = { AppBarActions(tab.actions) },
                )
            else
                // KMK <--
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(titleRes)) },
                    searchEnabled = searchEnabled,
                    searchQuery = if (searchEnabled) searchQuery else null,
                    onChangeSearchQuery = onChangeSearchQuery,
                    actions = { AppBarActions(tab.actions) },
                )
        },
        // KMK -->
        bottomBar = {
            LibraryBottomActionMenu(
                visible = feedScreenState.selectionMode,
                onChangeCategoryClicked = { feedScreenModel.addFavorite() },
                onMarkAsReadClicked = { libraryScreenModel.markReadSelection(true) },
                onMarkAsUnreadClicked = { libraryScreenModel.markReadSelection(false) },
                onDownloadClicked = libraryScreenModel::runDownloadActionSelection,
                onDeleteClicked = libraryScreenModel::openDeleteMangaDialog,
                onClickCleanTitles = null,
                onClickMigrate = null,
                onClickAddToMangaDex = null,
            )
        },
        // KMK <--
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.currentPage,
                modifier = Modifier.zIndex(1f),
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.currentPage == index,
                        onClick = { scope.launch { state.animateScrollToPage(index) } },
                        text = { TabText(text = stringResource(tab.titleRes), badgeCount = tab.badgeNumber) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    snackbarHostState,
                )
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)

// KMK -->
@Composable
private fun FeedSelectionToolbar(
    selectedCount: Int,
    onClickClearSelection: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_bookmark),
                        icon = Icons.Outlined.BookmarkAdd,
                        // TODO: method to add bookmark goes here
                        onClick = { },
                    ),
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onClickClearSelection,
    )
}
// KMK <--
