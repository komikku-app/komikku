@file:Suppress("FunctionName")

package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.presentation.manga.MangaScreenItem
import eu.kanade.tachiyomi.ui.manga.PagePreviewState
import exh.util.floor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
private fun PagePreviewLoading(
    setMaxWidth: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .height(60.dp)
            .fillMaxWidth()
            .onGloballyPositioned {
                setMaxWidth(with(density) { it.size.width.toDp() })
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PagePreviewRow(
    onOpenPage: (Int) -> Unit,
    items: ImmutableList<PagePreview>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        items.forEach { page ->
            PagePreview(
                modifier = Modifier.weight(1F),
                page = page,
                onOpenPage = onOpenPage,
            )
        }
    }
}

@Composable
private fun PagePreviewMore(
    onMorePreviewsClicked: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onMorePreviewsClicked) {
            Text(stringResource(SYMR.strings.more_previews))
        }
    }
}

@Composable
fun PagePreviews(
    pagePreviewState: PagePreviewState,
    onOpenPage: (Int) -> Unit,
    onMorePreviewsClicked: () -> Unit,
    rowCount: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        var maxWidth by remember {
            mutableStateOf(Dp.Hairline)
        }
        when {
            pagePreviewState is PagePreviewState.Loading || maxWidth == Dp.Hairline -> {
                PagePreviewLoading(setMaxWidth = { maxWidth = it })
            }
            pagePreviewState is PagePreviewState.Success -> {
                val itemPerRowCount = (maxWidth / 120.dp).floor()
                pagePreviewState.pagePreviews.take(rowCount * itemPerRowCount).chunked(itemPerRowCount).forEach {
                    PagePreviewRow(
                        onOpenPage = onOpenPage,
                        items = remember(it) { it.toImmutableList() },
                    )
                }

                PagePreviewMore(onMorePreviewsClicked)
            }
            else -> {}
        }
    }
}

fun LazyListScope.PagePreviewItems(
    pagePreviewState: PagePreviewState,
    onOpenPage: (Int) -> Unit,
    onMorePreviewsClicked: () -> Unit,
    maxWidth: Dp,
    setMaxWidth: (Dp) -> Unit,
    rowCount: Int,
) {
    when {
        pagePreviewState is PagePreviewState.Loading || maxWidth == Dp.Hairline -> {
            item(
                key = MangaScreenItem.CHAPTER_PREVIEW_LOADING,
                contentType = MangaScreenItem.CHAPTER_PREVIEW_LOADING,
            ) {
                PagePreviewLoading(setMaxWidth = setMaxWidth)
            }
        }
        pagePreviewState is PagePreviewState.Success -> {
            val itemPerRowCount = (maxWidth / 120.dp).floor()
            items(
                key = { "${MangaScreenItem.CHAPTER_PREVIEW_ROW}-${it.hashCode()}" },
                contentType = { MangaScreenItem.CHAPTER_PREVIEW_ROW },
                items = pagePreviewState.pagePreviews.take(rowCount * itemPerRowCount).chunked(itemPerRowCount),
            ) {
                PagePreviewRow(
                    onOpenPage = onOpenPage,
                    items = remember(it) { it.toImmutableList() },
                )
            }
            item(
                key = MangaScreenItem.CHAPTER_PREVIEW_MORE,
                contentType = MangaScreenItem.CHAPTER_PREVIEW_MORE,
            ) {
                PagePreviewMore(onMorePreviewsClicked)
            }
        }
        else -> {}
    }
}

@Composable
fun PagePreview(
    modifier: Modifier,
    page: PagePreview,
    onOpenPage: (Int) -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpenPage(page.index - 1) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SubcomposeAsyncImage(
            model = page,
            contentDescription = null,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val progress by page.progress.collectAsState()
                    if (progress < 0) {
                        CircularProgressIndicator()
                    } else {
                        CircularProgressIndicator(
                            progress = { progress / 0.01F },
                        )
                    }
                }
            },
            success = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    this@SubcomposeAsyncImage.SubcomposeAsyncImageContent(
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            },
            modifier = Modifier
                .height(200.dp)
                .width(120.dp),
        )
        Text(page.index.toString())
    }
}
