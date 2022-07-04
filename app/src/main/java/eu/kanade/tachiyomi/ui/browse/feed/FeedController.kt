package eu.kanade.tachiyomi.ui.browse.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.GlobalSearchControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
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
open class FeedController :
    NucleusController<GlobalSearchControllerBinding, FeedPresenter>(),
    FeedCardAdapter.OnMangaClickListener,
    FeedAdapter.OnFeedClickListener {

    init {
        setHasOptionsMenu(true)
    }

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: FeedAdapter? = null

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
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        parentController?.router?.pushController(MangaController(manga.id!!, true))
    }

    /**
     * Called when manga in global search is long clicked.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaLongClick(manga: Manga) {
        // Delegate to single click by default.
        onMangaClick(manga)
    }

    override fun createBinding(inflater: LayoutInflater): GlobalSearchControllerBinding = GlobalSearchControllerBinding.inflate(inflater)

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = FeedAdapter(this)

        // Create recycler and set adapter.
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        adapter?.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        adapter?.onRestoreInstanceState(savedViewState)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param source used to find holder containing source
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(feed: FeedSavedSearch): FeedHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition)
            if (item != null && feed.id == item.feed.id) {
                return holder as FeedHolder
            }
        }

        return null
    }

    /**
     * Add search result to adapter.
     *
     * @param feedManga the source items containing the latest manga.
     */
    fun setItems(feedManga: List<FeedItem>) {
        adapter?.updateDataSet(feedManga)

        if (feedManga.isEmpty()) {
            binding.emptyView.show(R.string.feed_tab_empty)
        } else {
            binding.emptyView.hide()
        }
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(feed: FeedSavedSearch, manga: Manga) {
        getHolder(feed)?.setImage(manga)
    }

    /**
     * Opens a catalogue with the given search.
     */
    override fun onSourceClick(source: CatalogueSource) {
        presenter.preferences.lastUsedSource().set(source.id)
        parentController?.router?.pushController(LatestUpdatesController(source))
    }

    override fun onSavedSearchClick(savedSearch: SavedSearch, source: CatalogueSource) {
        presenter.preferences.lastUsedSource().set(savedSearch.source)
        parentController?.router?.pushController(BrowseSourceController(source, savedSearch = savedSearch.id))
    }

    override fun onRemoveClick(feedSavedSearch: FeedSavedSearch) {
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
