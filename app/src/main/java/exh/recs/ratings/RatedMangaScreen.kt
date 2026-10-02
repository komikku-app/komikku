package exh.recs.ratings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.MangaItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class RatedMangaScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RatedMangaScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.rated_manga_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val current = state) {
                is RatedMangaScreenModel.State.Loading -> CenteredMessage(contentPadding) {
                    CircularProgressIndicator()
                }
                is RatedMangaScreenModel.State.Error -> CenteredMessage(contentPadding) {
                    Text(
                        text = stringResource(MR.strings.rated_manga_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is RatedMangaScreenModel.State.Success -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = contentPadding,
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RatingFilterRow(current.rating, screenModel::selectRating)
                    }
                    if (current.entries.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(MR.strings.rated_manga_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(current.entries, key = { "${it.taste.source}|${it.taste.url}" }) { entry ->
                        MangaItem(
                            title = entry.manga.title,
                            cover = entry.manga.asMangaCover(),
                            isFavorite = entry.manga.favorite,
                            onClick = { navigator.push(MangaScreen(entry.manga.id, true)) },
                            onLongClick = { navigator.push(MangaScreen(entry.manga.id, true)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingFilterRow(
    current: MangaRating,
    onSelect: (MangaRating) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            MangaRating.LOVE to MR.strings.manga_rating_love,
            MangaRating.LIKE to MR.strings.manga_rating_like,
            MangaRating.DISLIKE to MR.strings.manga_rating_dislike,
            MangaRating.NOT_INTERESTED to MR.strings.manga_rating_not_interested,
        ).forEach { (rating, label) ->
            FilterChip(
                selected = current == rating,
                onClick = { onSelect(rating) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
