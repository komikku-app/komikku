package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
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
    content = {
        FeedScreen(
            presenter = presenter,
            onClickAdd = {
                presenter.openAddSearchDialog(it)
            },
            onClickCreate = { source, savedSearch ->
                presenter.createFeed(source, savedSearch)
            },
            onClickSavedSearch = { savedSearch, source ->
                presenter.preferences.lastUsedSource().set(savedSearch.source)
                router?.pushController(BrowseSourceController(source, savedSearch = savedSearch.id))
            },
            onClickSource = { source ->
                presenter.preferences.lastUsedSource().set(source.id)
                router?.pushController(LatestUpdatesController(source))
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
