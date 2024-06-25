package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.browse.RelatedMangaTitle
import eu.kanade.presentation.browse.RelatedMangasLoadingItem
import eu.kanade.tachiyomi.ui.manga.RelatedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RelatedMangasList(
    relatedMangas: List<RelatedManga>,
    getManga: @Composable (Manga) -> State<Manga>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Manga>,
) {
    FastScrollLazyColumn(
        // Using modifier instead of contentPadding so we can use stickyHeader
        modifier = Modifier.padding(contentPadding),
    ) {
        relatedMangas.forEach { related ->
            val isLoading = related is RelatedManga.Loading
            if (isLoading) {
                item(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX${related.hashCode()}#header") {
                    RelatedMangaTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.padding.small,
                                end = MaterialTheme.padding.medium,
                            )
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                item(key = "${related.hashCode()}#content") { RelatedMangasLoadingItem() }
            } else {
                val relatedManga = related as RelatedManga.Success
                item(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX${related.hashCode()}#header") {
                    RelatedMangaTitle(
                        title = if (relatedManga.keyword.isNotBlank()) {
                            stringResource(KMR.strings.related_mangas_more)
                        } else {
                            stringResource(KMR.strings.pref_source_related_mangas)
                        },
                        subtitle = null,
                        onClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordClick(relatedManga.keyword)
                        },
                        onLongClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordLongClick(relatedManga.keyword)
                        },
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.padding.small,
                                end = MaterialTheme.padding.medium,
                            )
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                items(
                    key = { "related-list-${relatedManga.mangaList[it].id}" },
                    count = relatedManga.mangaList.size,
                ) { index ->
                    val manga by getManga(relatedManga.mangaList[index])
                    BrowseSourceListItem(
                        manga = manga,
                        onClick = { onMangaClick(manga) },
                        onLongClick = { onMangaLongClick(manga) },
                        isSelected = selection.fastAny { selected -> selected.id == manga.id },
                        metadata = null,
                    )
                }
            }
        }
    }
}
