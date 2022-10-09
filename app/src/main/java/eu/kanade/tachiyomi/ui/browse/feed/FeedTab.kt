package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController

@Composable
fun feedTab(
    router: Router?,
    presenter: FeedPresenter,
) = TabContent(
    titleRes = R.string.feed,
    actions = listOf(
        AppBar.Action(
            title = stringResource(R.string.action_add),
            icon = Icons.Outlined.Add,
            onClick = {
                presenter.openAddDialog()
            },
        ),
    ),
    content = { contentPadding ->
        FeedScreen(
            presenter = presenter,
            contentPadding = contentPadding,
            onClickAdd = {
                presenter.openAddSearchDialog(it)
            },
            onClickCreate = { source, savedSearch ->
                presenter.createFeed(source, savedSearch)
            },
            onClickSavedSearch = { savedSearch, source ->
                presenter.sourcePreferences.lastUsedSource().set(savedSearch.source)
                router?.pushController(BrowseSourceController(source, savedSearch = savedSearch.id))
            },
            onClickSource = { source ->
                presenter.sourcePreferences.lastUsedSource().set(source.id)
                router?.pushController(BrowseSourceController(source, GetRemoteManga.QUERY_LATEST))
            },
            onClickDelete = {
                presenter.dialog = FeedPresenter.Dialog.DeleteFeed(it)
            },
            onClickDeleteConfirm = {
                presenter.deleteFeed(it)
            },
            onClickManga = { manga ->
                router?.pushController(MangaController(manga.id, true))
            },
        )
    },
)
