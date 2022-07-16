package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.PagePreviewState
import exh.util.floor

@Composable
fun PagePreviews(pagePreviewState: PagePreviewState, onMorePreviewsClicked: () -> Unit) {
    when (pagePreviewState) {
        PagePreviewState.Loading -> {
            Box(modifier = Modifier.height(60.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is PagePreviewState.Success -> {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val itemPerRowCount = (maxWidth / 120.dp).floor()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pagePreviewState.pagePreviews.take(4 * itemPerRowCount).chunked(itemPerRowCount).forEach {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            it.forEach { page ->
                                PagePreview(
                                    modifier = Modifier.weight(1F),
                                    page = page,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onMorePreviewsClicked) {
                        Text(stringResource(R.string.more_previews))
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
fun PagePreview(
    modifier: Modifier,
    page: PagePreview,
) {
    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SubcomposeAsyncImage(
            model = page,
            contentDescription = null,
            loading = {
                val progress by page.progress.collectAsState()
                if (progress != -1) {
                    CircularProgressIndicator(progress / 0.01F)
                } else {
                    CircularProgressIndicator()
                }
            },
            success = {
                SubcomposeAsyncImageContent(
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .heightIn(max = 200.dp),
                    contentScale = ContentScale.FillWidth,
                )
            },
        )
        Text(page.index.toString())
    }
}
