package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.sources.SourceCategoryContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreenState
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun SourceCategoryScreen(
    state: SourceCategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(R.string.action_edit_categories),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                textResource = R.string.no_source_categories,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        SourceCategoryContent(
            categories = state.categories,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues + PaddingValues(horizontal = MaterialTheme.padding.medium),
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
        )
    }
}
