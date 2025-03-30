package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.browse.RelatedAnimeTitle
import eu.kanade.presentation.browse.RelatedAnimesLoadingItem
import eu.kanade.presentation.browse.header
import eu.kanade.presentation.library.components.CommonAnimeItemDefaults
import eu.kanade.tachiyomi.ui.anime.RelatedAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun RelatedAnimesCompactGrid(
    relatedAnimes: List<RelatedAnime>,
    getAnime: @Composable (Anime) -> State<Anime>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Anime) -> Unit,
    onMangaLongClick: (Anime) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Anime>,
) {
    FastScrollLazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(horizontal = MaterialTheme.padding.small),
        // padding for scrollbar
        topContentPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridHorizontalSpacer),
    ) {
        relatedAnimes.forEach { related ->
            val isLoading = related is RelatedAnime.Loading
            if (isLoading) {
                header(key = "${related.hashCode()}#header") {
                    RelatedAnimeTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                header(key = "${related.hashCode()}#content") { RelatedAnimesLoadingItem() }
            } else {
                val relatedAnime = related as RelatedAnime.Success
                header(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                header(key = "${related.hashCode()}#header") {
                    RelatedAnimeTitle(
                        title = if (relatedAnime.keyword.isNotBlank()) {
                            stringResource(KMR.strings.related_mangas_more)
                        } else {
                            stringResource(KMR.strings.related_mangas_website_suggestions)
                        },
                        showArrow = relatedAnime.keyword.isNotBlank(),
                        subtitle = null,
                        onClick = {
                            if (relatedAnime.keyword.isNotBlank()) onKeywordClick(relatedAnime.keyword)
                        },
                        onLongClick = {
                            if (relatedAnime.keyword.isNotBlank()) onKeywordLongClick(relatedAnime.keyword)
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                items(
                    key = { "related-compact-${relatedAnime.animeList[it].url.hashCode()}" },
                    count = relatedAnime.animeList.size,
                ) { index ->
                    val manga by getAnime(relatedAnime.animeList[index])
                    BrowseSourceCompactGridItem(
                        anime = manga,
                        onClick = { onMangaClick(manga) },
                        onLongClick = { onMangaLongClick(manga) },
                        isSelected = selection.fastAny { selected -> selected.id == manga.id },
                    )
                }
            }
        }
    }
}
