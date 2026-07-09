package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import androidx.compose.runtime.remember
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaInfoButtons(
    showRecommendsButton: Boolean,
    showMergeWithAnotherButton: Boolean,
    onRecommendClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // Sort merged entries by sources
    showMergedSources: Boolean,
    selectedSource: Source?,
    mergedMangaData: MergedMangaData?,
    filterMergedMangaBySource: (source: Source) -> Unit,
) {
    if (showRecommendsButton || showMergeWithAnotherButton || showMergedSources) {
        Column(Modifier.fillMaxWidth()) {
            if (showMergeWithAnotherButton) {
                Button(
                    onClick = onMergeWithAnotherClicked,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(SYMR.strings.merge_with_another_source))
                }
            }
            if (showRecommendsButton) {
                // KMK -->
                OutlinedButtonWithArrow(
                    text = stringResource(SYMR.strings.az_recommends),
                    onClick = onRecommendClicked,
                )
                // KMK <--
            }
            if (showMergedSources) {

                // The sources are sorted by chapter priority in the references

                val sources = remember(mergedMangaData) {
                    // Create a Map<SourceId, ChapterPriority> from the references
                    val priorities = mergedMangaData?.references?.associate { it.mangaSourceId to it.chapterPriority }
                    // Then sorts the sources by the chapter priority
                    mergedMangaData?.sources?.sortedBy{ priorities?.get(it.id) ?: Int.MAX_VALUE }
                } ?: emptyList()

                val selectedIndex = if (selectedSource == null) 0 else sources.indexOfFirst { it.id == selectedSource.id }

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .animateContentSize(animationSpec = spring())
                        .fillMaxWidth(),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    sources.forEach { source ->
                        val isSelected = source.id == selectedSource?.id
                        Tab(
                            selected = isSelected,
                            onClick = {filterMergedMangaBySource(source)},
                            text = { Text(source.name, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}
