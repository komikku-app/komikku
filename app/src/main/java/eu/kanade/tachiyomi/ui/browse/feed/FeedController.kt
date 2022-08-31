package eu.kanade.tachiyomi.ui.browse.feed

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [FeedPresenter]
 * [FeedCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
class FeedController : ComposeController<FeedPresenter>() {

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return applicationContext?.getString(R.string.feed)
    }

    /**
     * Create the [FeedPresenter] used in controller.
     *
     * @return instance of [FeedPresenter]
     */
    override fun createPresenter(): FeedPresenter {
        return FeedPresenter()
    }

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        FeedScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickSavedSearch = ::onSavedSearchClick,
            onClickSource = ::onSourceClick,
            onClickDelete = ::onRemoveClick,
            onClickManga = ::onMangaClick,
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.feed, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_feed -> addFeed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addFeed() {
        viewScope.launchUI {
            if (presenter.hasTooManyFeeds()) {
                activity?.toast(R.string.too_many_in_feed)
                return@launchUI
            }
            val items = presenter.getEnabledSources()
            val itemsStrings = items.map { it.toString() }
            var selectedIndex = 0

            MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.feed)
                .setSingleChoiceItems(itemsStrings.toTypedArray(), selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    addFeedSearch(items[selectedIndex])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun addFeedSearch(source: CatalogueSource) {
        viewScope.launchUI {
            val items = presenter.getSourceSavedSearches(source.id)
            val itemsStrings = listOf(activity!!.getString(R.string.latest)) + items.map { it.name }
            var selectedIndex = 0

            MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.feed)
                .setSingleChoiceItems(itemsStrings.toTypedArray(), selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    presenter.createFeed(source, items.getOrNull(selectedIndex - 1))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    private fun onMangaClick(manga: eu.kanade.domain.manga.model.Manga) {
        // Open MangaController.
        parentController?.router?.pushController(MangaController(manga.id, true))
    }

    /**
     * Opens a catalogue with the given search.
     */
    private fun onSourceClick(source: CatalogueSource) {
        presenter.preferences.lastUsedSource().set(source.id)
        parentController?.router?.pushController(LatestUpdatesController(source))
    }

    private fun onSavedSearchClick(savedSearch: SavedSearch, source: CatalogueSource) {
        presenter.preferences.lastUsedSource().set(savedSearch.source)
        parentController?.router?.pushController(BrowseSourceController(source, savedSearch = savedSearch.id))
    }

    private fun onRemoveClick(feedSavedSearch: FeedSavedSearch) {
        MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.feed)
            .setMessage(R.string.feed_delete)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                presenter.deleteFeed(feedSavedSearch)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
