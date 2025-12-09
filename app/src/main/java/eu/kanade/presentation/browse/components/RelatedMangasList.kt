package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
        relatedMangas.forEach { relatedManga ->
            when (relatedManga) {
                is RelatedManga.Loading -> {
                    stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX-${relatedManga.hashCode()}#header") {
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            HorizontalDivider()
                            RelatedMangaTitle(
                                title = stringResource(MR.strings.loading),
                                subtitle = null,
                                onClick = {},
                                onLongClick = null,
                                modifier = Modifier
                                    .padding(
                                        start = MaterialTheme.padding.small,
                                        end = MaterialTheme.padding.small,
                                    ),
                            )
                        }
                    }
                    item(key = "${relatedManga.hashCode()}#loading") { RelatedMangasLoadingItem() }
                }
                is RelatedManga.Success -> {
                    val hasKeyword = relatedManga.keyword.isNotBlank()
                    stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX-${relatedManga.hashCode()}#header") {
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            HorizontalDivider()
                            RelatedMangaTitle(
                                title = if (hasKeyword) {
                                    stringResource(KMR.strings.related_mangas_more)
                                } else {
                                    stringResource(KMR.strings.related_mangas_website_suggestions)
                                },
                                showArrow = hasKeyword,
                                subtitle = null,
                                onClick = {
                                    if (hasKeyword) onKeywordClick(relatedManga.keyword)
                                },
                                onLongClick = {
                                    if (hasKeyword) onKeywordLongClick(relatedManga.keyword)
                                },
                                modifier = Modifier
                                    .padding(
                                        start = MaterialTheme.padding.small,
                                        end = MaterialTheme.padding.small,
                                    ),
                            )
                        }
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
}
