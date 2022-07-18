package exh.pagepreview.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AroundLayout
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.manga.components.PagePreview
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import exh.pagepreview.PagePreviewState
import exh.util.floor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PagePreviewScreen(
    state: PagePreviewState,
    pageDialogOpen: Boolean,
    onPageSelected: (Int) -> Unit,
    onOpenPageDialog: () -> Unit,
    onDismissPageDialog: () -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .statusBarsPadding(),
        topBar = {
            PagePreviewTopAppBar(
                navigateUp = navigateUp,
                title = stringResource(R.string.page_previews),
                onOpenPageDialog = onOpenPageDialog,
                showOpenPageDialog = state is PagePreviewState.Success &&
                    (state.pageCount != null && state.pageCount > 1 /* TODO support unknown pageCount || state.hasNextPage*/),
            )
        },
    ) { paddingValues ->
        when (state) {
            is PagePreviewState.Error -> EmptyScreen(state.error.message.orEmpty())
            PagePreviewState.Loading -> LoadingScreen()
            is PagePreviewState.Success -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val itemPerRowCount by derivedStateOf { (maxWidth / 120.dp).floor() }
                    val items by derivedStateOf { state.pagePreviews.chunked(itemPerRowCount) }
                    val lazyListState = key(state.page) {
                        rememberLazyListState()
                    }
                    ScrollbarLazyColumn(
                        state = lazyListState,
                        modifier = Modifier,
                        contentPadding = paddingValues + topPaddingValues,
                    ) {
                        items.forEach {
                            item {
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
                        }
                    }
                }
            }
        }
    }
    if (pageDialogOpen && state is PagePreviewState.Success) {
        PagePreviewPageDialog(
            currentPage = state.page,
            pageCount = state.pageCount!!,
            onDismissPageDialog = onDismissPageDialog,
            onPageSelected = onPageSelected,
        )
    }
}

@Composable
fun PagePreviewPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismissPageDialog: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    var page by remember(currentPage) {
        mutableStateOf(currentPage.toFloat())
    }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismissPageDialog,
        confirmButton = {
            TextButton(onClick = {
                onPageSelected(page.roundToInt())
                onDismissPageDialog()
            },) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissPageDialog) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(stringResource(R.string.page_preview_page_go_to))
        },
        text = {
            AroundLayout(
                startLayout = { Text(text = page.roundToInt().toString()) },
                endLayout = { Text(text = pageCount.toString()) },
            ) {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = page,
                    onValueChange = { page = it },
                    onValueChangeFinished = {
                        scope.launch {
                            val newPage = page
                            AnimationState(
                                newPage,
                            ).animateTo(newPage.roundToInt().toFloat()) {
                                page = value
                            }
                        }
                    },
                    valueRange = 1F..pageCount.toFloat(),
                )
            }
        },
    )
}

@Composable
fun PagePreviewTopAppBar(
    navigateUp: () -> Unit,
    title: String,
    onOpenPageDialog: () -> Unit,
    showOpenPageDialog: Boolean,
) {
    AppBar(
        title = title,
        actions = {
            if (showOpenPageDialog) {
                IconButton(onClick = onOpenPageDialog) {
                    Icon(
                        imageVector = Icons.Default.UTurnRight,
                        contentDescription = stringResource(R.string.page_preview_page_go_to),
                    )
                }
            }
        },
        navigateUp = navigateUp,
    )
}
