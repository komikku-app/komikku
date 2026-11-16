package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.navigation.AppNavigatorHost
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Library destination: categories as tabs, a cover grid per category; manga open their details. */
data object LibraryTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.label_library),
            icon = rememberVectorPainter(Icons.Outlined.CollectionsBookmark),
        )

    @Composable
    override fun Content() {
        AppNavigatorHost(screen = LibraryHomeScreen)
    }
}

/** The library grid; manga open the details screen in the library tab's own navigator. */
data object LibraryHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val state by screenModel.state.collectAsState()
        when {
            state.isLoading -> LoadingScreen()
            state.query.isEmpty() && !state.hasActiveFilters && state.isLibraryEmpty ->
                EmptyScreen(stringRes = MR.strings.information_empty_library)
            else -> LibraryPages(
                state = state,
                onQueryChange = screenModel::search,
                onSelectCategory = screenModel::selectCategory,
                onOpen = { navigator.push(MangaDetailsScreen(it)) },
            )
        }
    }
}

@Composable
private fun LibraryPages(
    state: LibraryScreenModel.State,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val pages = state.pages
    val index = state.selectedIndex
    val runner = remember { Injekt.get<LibraryUpdateRunner>() }
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val showCounts by libraryPreferences.categoryNumberOfItems().collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        // Library refresh (6L.2): the shown category, or the whole library; search (8.5)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("library-search"),
            )
            TextButton(
                onClick = { runner.start(LibraryUpdateRunner.Target.Category(pages[index].category.id)) },
                modifier = Modifier.testTag("update-category"),
            ) { Text(stringResource(MR.strings.action_update_category)) }
            TextButton(
                onClick = { runner.start(LibraryUpdateRunner.Target.All) },
                modifier = Modifier.testTag("update-library-all"),
            ) { Text(stringResource(MR.strings.action_update_library)) }
        }
        LibraryUpdateBanner(runner)
        if (pages.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
                pages.forEachIndexed { i, page ->
                    Tab(
                        selected = i == index,
                        onClick = { onSelectCategory(page.category.id) },
                        text = {
                            Text(
                                // Android shows counts when the preference is on or a search is active.
                                text = page.title() +
                                    if (showCounts || state.query.isNotEmpty()) " (${page.items.size})" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("library-tab-${page.category.id}"),
                    )
                }
            }
        }
        val page = pages[index]
        if (page.items.isEmpty()) {
            // Android's LibraryPagerEmptyScreen
            EmptyScreen(
                stringRes = when {
                    state.query.isNotEmpty() -> MR.strings.no_results_found
                    state.hasActiveFilters -> MR.strings.error_no_match
                    else -> MR.strings.information_no_manga_category
                },
            )
        } else {
            LibraryGrid(page.items, onOpen)
        }
    }
}

@Composable
private fun LibraryPage.title(): String =
    if (category.isSystemCategory) stringResource(MR.strings.label_default) else category.name

@Composable
private fun LibraryGrid(items: List<LibraryItem>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.id }) { item ->
            LibraryGridItem(item, onClick = { onOpen(item.id) })
        }
    }
}

@Composable
private fun LibraryGridItem(libraryItem: LibraryItem, onClick: () -> Unit) {
    val item = libraryItem.libraryManga
    val cover = remember(item.manga) { item.manga.asMangaCover() }
    Column(modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MANGA_COVER_RATIO),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = item.manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
            )
            // LibraryItem.unreadCount is 0 when the unread badge preference is off (Android).
            if (libraryItem.unreadCount > 0) {
                Box(contentAlignment = Alignment.TopStart, modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = libraryItem.unreadCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .testTag("unread-badge-${item.manga.id}"),
                    )
                }
            }
        }
        Text(
            text = item.manga.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val MANGA_COVER_RATIO = 2f / 3f
