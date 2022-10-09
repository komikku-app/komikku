package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.feed.FeedPresenter
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView.Companion.bottomNavPadding
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import eu.kanade.domain.manga.model.MangaCover as MangaCoverData

data class FeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: CatalogueSource?,
    val title: String,
    val subtitle: String,
    val results: List<Manga>?,
)

@Composable
fun FeedScreen(
    presenter: FeedPresenter,
    onClickAdd: (CatalogueSource) -> Unit,
    onClickCreate: (CatalogueSource, SavedSearch?) -> Unit,
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickDeleteConfirm: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.isEmpty -> EmptyScreen(R.string.feed_tab_empty)
        else -> {
            FeedList(
                state = presenter,
                getMangaState = { item, source -> presenter.getManga(item, source) },
                onClickSavedSearch = onClickSavedSearch,
                onClickSource = onClickSource,
                onClickDelete = onClickDelete,
                onClickManga = onClickManga,
            )
        }
    }

    when (val dialog = presenter.dialog) {
        is FeedPresenter.Dialog.AddFeed -> {
            FeedAddDialog(
                sources = dialog.options,
                onDismiss = { presenter.dialog = null },
                onClickAdd = {
                    presenter.dialog = null
                    onClickAdd(it ?: return@FeedAddDialog)
                },
            )
        }
        is FeedPresenter.Dialog.AddFeedSearch -> {
            FeedAddSearchDialog(
                source = dialog.source,
                savedSearches = dialog.options,
                onDismiss = { presenter.dialog = null },
                onClickAdd = { source, savedSearch ->
                    presenter.dialog = null
                    onClickCreate(source, savedSearch)
                },
            )
        }
        is FeedPresenter.Dialog.DeleteFeed -> {
            FeedDeleteConfirmDialog(
                feed = dialog.feed,
                onDismiss = { presenter.dialog = null },
                onClickDeleteConfirm = {
                    presenter.dialog = null
                    onClickDeleteConfirm(it)
                },
            )
        }
        null -> Unit
    }
}

@Composable
fun FeedList(
    state: FeedState,
    getMangaState: @Composable ((Manga, CatalogueSource?) -> State<Manga>),
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = bottomNavPadding + topPaddingValues,
    ) {
        items(
            state.items.orEmpty(),
            key = { it.feed.id },
        ) { item ->
            FeedItem(
                modifier = Modifier.animateItemPlacement(),
                item = item,
                getMangaState = { getMangaState(it, item.source) },
                onClickSavedSearch = onClickSavedSearch,
                onClickSource = onClickSource,
                onClickDelete = onClickDelete,
                onClickManga = onClickManga,
            )
        }
    }
}

@Composable
fun FeedItem(
    modifier: Modifier,
    item: FeedItemUI,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickSavedSearch: (SavedSearch, CatalogueSource) -> Unit,
    onClickSource: (CatalogueSource) -> Unit,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    Column(
        modifier then Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClick = {
                        onClickDelete(item.feed)
                    },
                    onClick = {
                        if (item.savedSearch != null && item.source != null) {
                            onClickSavedSearch(item.savedSearch, item.source)
                        } else if (item.source != null) {
                            onClickSource(item.source)
                        }
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.high),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                contentDescription = stringResource(R.string.label_more),
                modifier = Modifier.padding(16.dp),
            )
        }
        when {
            item.results == null -> {
                CircularProgressIndicator()
            }
            item.results.isEmpty() -> {
                Text(stringResource(R.string.no_results_found), modifier = Modifier.padding(bottom = 16.dp))
            }
            else -> {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    items(item.results) {
                        val manga by getMangaState(it)
                        FeedCardItem(
                            manga = manga,
                            onClickManga = onClickManga,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedCardItem(
    modifier: Modifier = Modifier,
    manga: Manga,
    onClickManga: (Manga) -> Unit,
) {
    Column(
        modifier
            .padding(vertical = 4.dp)
            .width(112.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = { onClickManga(manga) })
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            MangaCover.Book(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(
                        if (manga.favorite) 0.3f else 1.0f,
                    ),
                data = MangaCoverData(
                    manga.id,
                    manga.source,
                    manga.favorite,
                    manga.thumbnailUrl,
                    manga.coverLastModified,
                ),
            )
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                if (manga.favorite) {
                    Badge(text = stringResource(R.string.in_library))
                }
            }
        }

        Text(
            modifier = Modifier.padding(4.dp),
            text = manga.title,
            fontSize = 12.sp,
            maxLines = 2,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun FeedAddDialog(
    sources: List<CatalogueSource>,
    onDismiss: () -> Unit,
    onClickAdd: (CatalogueSource?) -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.feed))
        },
        text = {
            RadioSelector(options = sources, selected = selected) {
                selected = it
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onClickAdd(selected?.let { sources[it] }) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
fun FeedAddSearchDialog(
    source: CatalogueSource,
    savedSearches: List<SavedSearch?>,
    onDismiss: () -> Unit,
    onClickAdd: (CatalogueSource, SavedSearch?) -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        title = {
            Text(text = source.name)
        },
        text = {
            val context = LocalContext.current
            val savedSearchStrings = remember {
                savedSearches.map {
                    it?.name ?: context.getString(R.string.latest)
                }
            }
            RadioSelector(
                options = savedSearches,
                optionStrings = savedSearchStrings,
                selected = selected,
            ) {
                selected = it
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onClickAdd(source, selected?.let { savedSearches[it] }) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
fun <T> RadioSelector(
    options: List<T>,
    optionStrings: List<String> = remember { options.map { it.toString() } },
    selected: Int?,
    onSelectOption: (Int) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        optionStrings.forEachIndexed { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onSelectOption(index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected == index, onClick = null)
                Spacer(Modifier.width(4.dp))
                Text(option, maxLines = 1)
            }
        }
    }
}

@Composable
fun FeedDeleteConfirmDialog(
    feed: FeedSavedSearch,
    onDismiss: () -> Unit,
    onClickDeleteConfirm: (FeedSavedSearch) -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.feed))
        },
        text = {
            Text(text = stringResource(R.string.feed_delete))
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onClickDeleteConfirm(feed) }) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
    )
}
