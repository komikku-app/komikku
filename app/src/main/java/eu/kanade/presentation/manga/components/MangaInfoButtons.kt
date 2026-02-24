package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaInfoButtons(
    showRecommendsButton: Boolean,
    showMergeWithAnotherButton: Boolean,
    onRecommendClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // My stuff
    showMergedSources: Boolean,
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
            if (showMergedSources){
                val sources = mergedMangaData?.sources?.filter { it.name != "MergedSource" }
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .animateContentSize(animationSpec = spring())
                        .fillMaxWidth(),
                ) {
                    LazyRow(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .animateContentSize(animationSpec = spring())
                            .fillMaxWidth(),
                    ) {
                        items(items = sources ?: emptyList()) { source ->
                            Button(
                                onClick = {filterMergedMangaBySource(source)},
                                modifier = Modifier.padding(horizontal = 4.dp),


                            ) {
                                Text(source.name)
                            }

                        }
                    }
                }
            }

        }
    }
}
