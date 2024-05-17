package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    LazyColumn(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
        ),
        contentPadding = PaddingValues(MaterialTheme.padding.small),
    ) {
        relatedMangas.forEach {
            val isLoading = it is RelatedManga.Loading
            if (isLoading) {
                stickyHeader {
                    RelatedMangaTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                item { RelatedMangasLoadingItem() }
            } else {
                val relatedManga = it as RelatedManga.Success
                stickyHeader {
                    RelatedMangaTitle(
                        title = relatedManga.keyword,
                        subtitle = null,
                        onClick = { onKeywordClick(relatedManga.keyword) },
                        onLongClick = { onKeywordLongClick(relatedManga.keyword) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                items(count = relatedManga.mangaList.size) { index ->
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
