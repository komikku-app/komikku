package eu.kanade.tachiyomi.ui.browse.source.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.GlobalSearchControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.SearchableNucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterSheet
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import exh.util.nullIfBlank
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [SourceFeedPresenter]
 * [SourceFeedCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class SourceFeedController :
    SearchableNucleusController<GlobalSearchControllerBinding, SourceFeedPresenter>,
    FabController,
    SourceFeedCardAdapter.OnMangaClickListener,
    SourceFeedAdapter.OnFeedClickListener {

    constructor(source: CatalogueSource?) : super(
        bundleOf(
            SOURCE_EXTRA to (source?.id ?: 0),
        ),
    ) {
        this.source = source
    }

    constructor(sourceId: Long) : this(
        Injekt.get<SourceManager>().get(sourceId) as? CatalogueSource,
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(SOURCE_EXTRA))

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: SourceFeedAdapter? = null

    var source: CatalogueSource? = null

    private var actionFab: ExtendedFloatingActionButton? = null

    /**
     * Sheet containing filter items.
     */
    private var filterSheet: SourceFilterSheet? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return source!!.name
    }

    /**
     * Create the [SourceFeedPresenter] used in controller.
     *
     * @return instance of [SourceFeedPresenter]
     */
    override fun createPresenter(): SourceFeedPresenter {
        return SourceFeedPresenter(source!!)
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        router.pushController(MangaController(manga.id!!, true).withFadeTransaction())
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

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(menu, inflater, R.menu.global_search, R.id.action_search)
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        onBrowseClick(query.nullIfBlank())
    }

    override fun onSearchViewQueryTextChange(newText: String?) {
        if (router.backstack.lastOrNull()?.controller == this) {
            presenter.query = newText ?: ""
        }
    }

    override fun createBinding(inflater: LayoutInflater): GlobalSearchControllerBinding = GlobalSearchControllerBinding.inflate(inflater)

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Prepare filter sheet
        initFilterSheet()

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = SourceFeedAdapter(this)

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

    private val filterSerializer = FilterSerializer()

    fun initFilterSheet() {
        if (presenter.sourceFilters.isEmpty()) {
            actionFab?.text = activity!!.getString(R.string.saved_searches)
        }

        filterSheet = SourceFilterSheet(
            activity!!,
            // SY -->
            this,
            presenter.source,
            emptyList(),
            // SY <--
            onFilterClicked = {
                val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                filterSheet?.dismiss()
                if (allDefault) {
                    onBrowseClick(
                        presenter.query.nullIfBlank(),
                    )
                } else {
                    onBrowseClick(
                        presenter.query.nullIfBlank(),
                        filters = Json.encodeToString(filterSerializer.serialize(presenter.sourceFilters)),
                    )
                }
            },
            onResetClicked = {},
            onSaveClicked = {},
            onSavedSearchClicked = { idOfSearch ->
                viewScope.launchUI {
                    val search = presenter.loadSearch(idOfSearch)

                    if (search == null) {
                        filterSheet?.context?.let {
                            MaterialAlertDialogBuilder(it)
                                .setTitle(R.string.save_search_failed_to_load)
                                .setMessage(R.string.save_search_failed_to_load_message)
                                .show()
                        }
                        return@launchUI
                    }

                    if (search.filterList == null) {
                        activity?.toast(R.string.save_search_invalid)
                        return@launchUI
                    }

                    presenter.sourceFilters = FilterList(search.filterList)
                    filterSheet?.setFilters(presenter.filterItems)
                    val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                    filterSheet?.dismiss()

                    if (!allDefault) {
                        onBrowseClick(
                            search = presenter.query.nullIfBlank(),
                            savedSearch = search.id,
                        )
                    }
                }
            },
            onSavedSearchDeleteClicked = { idOfSearch, name ->
                viewScope.launchUI {
                    if (presenter.hasTooManyFeeds()) {
                        activity?.toast(R.string.too_many_in_feed)
                        return@launchUI
                    }
                    withUIContext {
                        MaterialAlertDialogBuilder(activity!!)
                            .setTitle(R.string.feed)
                            .setMessage(activity!!.getString(R.string.feed_add, name))
                            .setPositiveButton(R.string.action_add) { _, _ ->
                                presenter.createFeed(idOfSearch)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            },
        )
        launchUI {
            filterSheet?.setSavedSearches(presenter.loadSearches())
        }
        filterSheet?.setFilters(presenter.filterItems)

        // TODO: [ExtendedFloatingActionButton] hide/show methods don't work properly
        filterSheet?.setOnShowListener { actionFab?.isVisible = false }
        filterSheet?.setOnDismissListener { actionFab?.isVisible = true }

        actionFab?.setOnClickListener { filterSheet?.show() }

        actionFab?.isVisible = true
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab

        // Controlled by initFilterSheet()
        fab.isVisible = false

        fab.setText(R.string.action_filter)
        fab.setIconResource(R.drawable.ic_filter_list_24dp)
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFab = null
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param source used to find holder containing source
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(sourceFeed: SourceFeed): SourceFeedHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition)
            if (item != null && sourceFeed == item.sourceFeed) {
                return holder as SourceFeedHolder
            }
        }

        return null
    }

    /**
     * Add search result to adapter.
     *
     * @param feedManga the source items containing the latest manga.
     */
    fun setItems(feedManga: List<SourceFeedItem>) {
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
    fun onMangaInitialized(sourceFeed: SourceFeed, manga: Manga) {
        getHolder(sourceFeed)?.setImage(manga)
    }

    fun onBrowseClick(search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        router.replaceTopController(BrowseSourceController(presenter.source, search, savedSearch = savedSearch, filterList = filters).withFadeTransaction())
    }

    override fun onLatestClick() {
        router.replaceTopController(LatestUpdatesController(presenter.source).withFadeTransaction())
    }

    override fun onBrowseClick() {
        router.replaceTopController(BrowseSourceController(presenter.source).withFadeTransaction())
    }

    override fun onSavedSearchClick(savedSearch: SavedSearch) {
        router.replaceTopController(BrowseSourceController(presenter.source, savedSearch = savedSearch.id).withFadeTransaction())
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

    companion object {
        const val SOURCE_EXTRA = "source"
    }
}
