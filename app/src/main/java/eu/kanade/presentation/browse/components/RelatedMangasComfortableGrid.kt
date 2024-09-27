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
import eu.kanade.presentation.browse.RelatedMangaTitle
import eu.kanade.presentation.browse.RelatedMangasLoadingItem
import eu.kanade.presentation.browse.header
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.tachiyomi.ui.manga.RelatedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun RelatedMangasComfortableGrid(
    relatedMangas: List<RelatedManga>,
    getManga: @Composable (Manga) -> State<Manga>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Manga>,
    usePanoramaCover: Boolean? = null,
) {
    FastScrollLazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(horizontal = MaterialTheme.padding.small),
        // padding for scrollbar
        topContentPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        relatedMangas.forEach { related ->
            val isLoading = related is RelatedManga.Loading
            if (isLoading) {
                header(key = "${related.hashCode()}#header") {
                    RelatedMangaTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                header(key = "${related.hashCode()}#content") { RelatedMangasLoadingItem() }
            } else {
                val relatedManga = related as RelatedManga.Success
                header(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                header(key = "${related.hashCode()}#header") {
                    RelatedMangaTitle(
                        title = if (relatedManga.keyword.isNotBlank()) {
                            stringResource(KMR.strings.related_mangas_more)
                        } else {
                            stringResource(KMR.strings.related_mangas_website_suggestions)
                        },
                        showArrow = relatedManga.keyword.isNotBlank(),
                        subtitle = null,
                        onClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordClick(relatedManga.keyword)
                        },
                        onLongClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordLongClick(relatedManga.keyword)
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                items(
                    key = { "related-comfort-${relatedManga.mangaList[it].id}" },
                    count = relatedManga.mangaList.size,
                ) { index ->
                    val manga by getManga(relatedManga.mangaList[index])
                    BrowseSourceComfortableGridItem(
                        manga = manga,
                        onClick = { onMangaClick(manga) },
                        onLongClick = { onMangaLongClick(manga) },
                        isSelected = selection.fastAny { selected -> selected.id == manga.id },
                        metadata = null,
                        usePanoramaCover = usePanoramaCover,
                    )
                }
            }
        }
    }
}
