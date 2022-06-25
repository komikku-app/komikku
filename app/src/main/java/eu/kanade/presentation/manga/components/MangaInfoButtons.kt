package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.Button
import eu.kanade.tachiyomi.R

@Composable
fun MangaInfoButtons(
    showRecommendsButton: Boolean,
    showMergeWithAnotherButton: Boolean,
    onRecommendClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
) {
    if (showRecommendsButton || showMergeWithAnotherButton) {
        Column(Modifier.fillMaxWidth()) {
            if (showMergeWithAnotherButton) {
                Button(
                    onClick = onMergeWithAnotherClicked,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.merge_with_another_source))
                }
            }
            if (showRecommendsButton) {
                Button(
                    onClick = onRecommendClicked,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.az_recommends))
                }
            }
        }
    }
}
