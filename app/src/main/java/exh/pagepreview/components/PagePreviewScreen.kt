package exh.pagepreview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UTurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.PagePreview
import exh.pagepreview.PagePreviewState
import exh.util.floor
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun PagePreviewScreen(
    state: PagePreviewState,
    pageDialogOpen: Boolean,
    onPageSelected: (Int) -> Unit,
    onOpenPage: (Int) -> Unit,
    onOpenPageDialog: () -> Unit,
    onDismissPageDialog: () -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            PagePreviewTopAppBar(
                navigateUp = navigateUp,
                title = stringResource(SYMR.strings.page_previews),
                onOpenPageDialog = onOpenPageDialog,
                showOpenPageDialog = state is PagePreviewState.Success &&
                    (state.pageCount != null && state.pageCount > 1 /* TODO support unknown pageCount || state.hasNextPage*/),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when (state) {
            is PagePreviewState.Error -> EmptyScreen(state.error.message.orEmpty())
            PagePreviewState.Loading -> LoadingScreen()
            is PagePreviewState.Success -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val itemPerRowCount = remember(maxWidth) {
                        (maxWidth / 120.dp).floor()
                    }
                    val items = remember(state.pagePreviews, itemPerRowCount) {
                        state.pagePreviews.chunked(itemPerRowCount)
                    }
                    val lazyListState = key(state.page) {
                        rememberLazyListState()
                    }
                    ScrollbarLazyColumn(
                        state = lazyListState,
                        modifier = Modifier,
                        contentPadding = paddingValues + topSmallPaddingValues,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                it.forEach { page ->
                                    PagePreview(
                                        modifier = Modifier.weight(1F),
                                        page = page,
                                        onOpenPage = onOpenPage,
                                    )
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
    var pageText by remember(currentPage) {
        mutableStateOf(currentPage.toString())
    }
    AlertDialog(
        onDismissRequest = onDismissPageDialog,
        confirmButton = {
            TextButton(
                onClick = {
                    val page = pageText.toIntOrNull()?.coerceIn(1, pageCount) ?: currentPage
                    onPageSelected(page)
                    onDismissPageDialog()
                },
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissPageDialog) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.page_preview_page_go_to))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = pageText,
                onValueChange = { input ->
                    pageText = input.filter { it.isDigit() }
                },
                singleLine = true,
                label = { Text(stringResource(SYMR.strings.page_preview_page_go_to)) },
                suffix = { Text(text = " / $pageCount") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        },
    )
}

@Composable
fun PagePreviewTopAppBar(
    navigateUp: () -> Unit,
    title: String,
    onOpenPageDialog: () -> Unit,
    showOpenPageDialog: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = title,
        actions = {
            AppBarActions(
                if (showOpenPageDialog) {
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(SYMR.strings.page_preview_page_go_to),
                            icon = Icons.Outlined.UTurnRight,
                            onClick = onOpenPageDialog,
                        ),
                    )
                } else {
                    persistentListOf()
                },
            )
        },
        navigateUp = navigateUp,
        scrollBehavior = scrollBehavior,
    )
}
