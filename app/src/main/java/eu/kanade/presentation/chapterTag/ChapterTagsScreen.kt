package eu.kanade.presentation.chapterTag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.chapterTag.components.ChapterTagListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.chapterTag.ChapterTagsScreenState
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun ChapterTagsScreen(
    state: ChapterTagsScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (ChapterTag) -> Unit,
    onClickDelete: (ChapterTag) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.chapter_tags),
                navigateUp = navigateUp,
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
                stringRes = KMR.strings.information_empty_chapter_tags,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = paddingValues +
                topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(
                items = state.chapterTags,
                key = { chapterTag -> "chapter-tag-${chapterTag.id}" },
            ) { chapterTag ->
                ChapterTagListItem(
                    modifier = Modifier.animateItem(),
                    chapterTag = chapterTag,
                    onRename = { onClickRename(chapterTag) },
                    onDelete = { onClickDelete(chapterTag) },
                )
            }
        }
    }
}
