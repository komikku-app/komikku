package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.shared.navigation.AppNavigatorHost
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

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
        when (val current = state) {
            LibraryScreenModel.State.Loading -> LoadingScreen()
            is LibraryScreenModel.State.Success -> if (current.isEmpty) {
                EmptyScreen(stringRes = MR.strings.information_empty_library)
            } else {
                LibraryPages(current.pages, onOpen = { navigator.push(MangaDetailsScreen(it)) })
            }
        }
    }
}

@Composable
private fun LibraryPages(pages: List<LibraryPage>, onOpen: (Long) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, pages.lastIndex)
    Column(modifier = Modifier.fillMaxSize()) {
        if (pages.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
                pages.forEachIndexed { i, page ->
                    Tab(
                        selected = i == index,
                        onClick = { selected = i },
                        text = { Text(text = page.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        val page = pages[index]
        if (page.manga.isEmpty()) {
            EmptyScreen(stringRes = MR.strings.information_empty_category)
        } else {
            LibraryGrid(page.manga, onOpen)
        }
    }
}

@Composable
private fun LibraryPage.title(): String =
    if (category.isSystemCategory) stringResource(MR.strings.label_default) else category.name

@Composable
private fun LibraryGrid(manga: List<LibraryManga>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = manga, key = { it.manga.id }) { item ->
            LibraryGridItem(item, onClick = { onOpen(item.manga.id) })
        }
    }
}

@Composable
private fun LibraryGridItem(item: LibraryManga, onClick: () -> Unit) {
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
