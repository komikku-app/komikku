package eu.kanade.tachiyomi.ui.source.browse

import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.elvishew.xlog.XLog
import com.google.android.material.snackbar.Snackbar
import com.tfcporciuncula.flow.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues.DisplayMode
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.databinding.SourceControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.SecondaryDrawerController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.library.ChangeMangaCategoriesDialog
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.source.SourceController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import exh.savedsearches.EXHSavedSearch
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.main_activity.drawer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents
import uy.kohesive.injekt.injectLazy

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseSourceController(bundle: Bundle) :
    NucleusController<SourceControllerBinding, BrowseSourcePresenter>(bundle),
    SecondaryDrawerController,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.EndlessScrollListener,
    ChangeMangaCategoriesDialog.Listener {

    constructor(
        source: CatalogueSource,
        searchQuery: String? = null,
        smartSearchConfig: SourceController.SmartSearchConfig? = null
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)

            if (searchQuery != null) {
                putString(SEARCH_QUERY_KEY, searchQuery)
            }

            if (smartSearchConfig != null) {
                putParcelable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }
        }
    )

    private val preferences: PreferencesHelper by injectLazy()

    private val recommendsConfig: RecommendsConfig? = args.getParcelable(RECOMMENDS_CONFIG)

    // AZ -->
    private val mode = if (recommendsConfig == null) Mode.CATALOGUE else Mode.RECOMMENDS
    // AZ <--
    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Navigation view containing filter items.
     */
    private var navView: SourceFilterSheet? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Subscription for the number of manga per row.
     */
    private var numColumnsJob: Job? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return when (mode) {
            Mode.CATALOGUE -> presenter.source.name
            Mode.RECOMMENDS -> recommendsConfig!!.manga.title
        }
    }

    override fun createPresenter(): BrowseSourcePresenter {
        return BrowseSourcePresenter(
            args.getLong(SOURCE_ID_KEY),
            args.getString(SEARCH_QUERY_KEY),
            searchManga = if (mode == Mode.RECOMMENDS) recommendsConfig?.manga else null,
            recommends = (mode == Mode.RECOMMENDS)
        )
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = SourceControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(null, this)
        setupRecycler(view)

        navView?.setFilters(presenter.filterItems)

        binding.progress.visible()
    }

    override fun onDestroyView(view: View) {
        numColumnsJob?.cancel()
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    override fun createSecondaryDrawer(drawer: DrawerLayout): ViewGroup? {
        if (mode == Mode.RECOMMENDS) { return null }
        // Inflate and prepare drawer
        val navView = drawer.inflate(R.layout.source_drawer) as SourceFilterSheet // TODO whatever this is
        this.navView = navView
        navView.setFilters(presenter.filterItems)

        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)

        // EXH -->
        navView.setSavedSearches(presenter.loadSearches())
        navView.onSaveClicked = {
            MaterialDialog(navView.context)
                .title(text = "Save current search query?")
                .input("My search name", hintRes = null) { _, searchName ->
                    val oldSavedSearches = presenter.loadSearches()
                    if (searchName.isNotBlank() &&
                        oldSavedSearches.size < SourceFilterSheet.MAX_SAVED_SEARCHES
                    ) {
                        val newSearches = oldSavedSearches + EXHSavedSearch(
                            searchName.toString().trim(),
                            presenter.query,
                            presenter.sourceFilters
                        )
                        presenter.saveSearches(newSearches)
                        navView.setSavedSearches(newSearches)
                    }
                }
                .positiveButton(R.string.action_save)
                .negativeButton(R.string.action_cancel)
                .cancelable(true)
                .cancelOnTouchOutside(true)
                .show()
        }

        navView.onSavedSearchClicked = cb@{ indexToSearch ->
            val savedSearches = presenter.loadSearches()

            val search = savedSearches.getOrNull(indexToSearch)

            if (search == null) {
                MaterialDialog(navView.context)
                    .title(text = "Failed to load saved searches!")
                    .message(text = "An error occurred while loading your saved searches.")
                    .cancelable(true)
                    .cancelOnTouchOutside(true)
                    .show()
                return@cb
            }

            presenter.sourceFilters = FilterList(search.filterList)
            navView.setFilters(presenter.filterItems)
            val allDefault = presenter.sourceFilters == presenter.source.getFilterList()

            showProgressBar()
            adapter?.clear()
            drawer.closeDrawer(GravityCompat.END)
            presenter.restartPager(search.query, if (allDefault) FilterList() else presenter.sourceFilters)
            activity?.invalidateOptionsMenu()
        }

        navView.onSavedSearchDeleteClicked = cb@{ indexToDelete, name ->
            val savedSearches = presenter.loadSearches()

            val search = savedSearches.getOrNull(indexToDelete)

            if (search == null || search.name != name) {
                MaterialDialog(navView.context)
                    .title(text = "Failed to delete saved search!")
                    .message(text = "An error occurred while deleting the search.")
                    .cancelable(true)
                    .cancelOnTouchOutside(true)
                    .show()
                return@cb
            }

            MaterialDialog(navView.context)
                .title(text = "Delete saved search query?")
                .message(text = "Are you sure you wish to delete your saved search query: '${search.name}'?")
                .positiveButton(R.string.action_cancel)
                .negativeButton(text = "Confirm") {
                    val newSearches = savedSearches.filterIndexed { index, _ ->
                        index != indexToDelete
                    }
                    presenter.saveSearches(newSearches)
                    navView.setSavedSearches(newSearches)
                }
                .cancelable(true)
                .cancelOnTouchOutside(true)
                .show()
        }
        // EXH <--

        navView.onSearchClicked = {
            val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
            showProgressBar()
            adapter?.clear()
            drawer.closeDrawer(GravityCompat.END)
            presenter.setSourceFilter(if (allDefault) FilterList() else presenter.sourceFilters)
        }

        navView.onResetClicked = {
            presenter.appliedFilters = FilterList()
            val newFilters = presenter.source.getFilterList()
            presenter.sourceFilters = newFilters
            navView.setFilters(presenter.filterItems)
        }
        return navView as ViewGroup // TODO fix this bullshit
    }

    override fun cleanupSecondaryDrawer(drawer: DrawerLayout) {
        navView = null
    }

    private fun setupRecycler(view: View) {
        numColumnsJob?.cancel()

        var oldPosition = RecyclerView.NO_POSITION
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (preferences.catalogueDisplayMode().get() == DisplayMode.LIST) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManager(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } else {
            (binding.catalogueView.inflate(R.layout.source_recycler_autofit) as AutofitRecyclerView).apply {
                numColumnsJob = getColumnsPreferenceForCurrentOrientation().asImmediateFlow { spanCount = it }
                    .drop(1)
                    // Set again the adapter to recalculate the covers height
                    .onEach { adapter = this@BrowseSourceController.adapter }
                    .launchIn(scope)

                (layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.source_compact_grid_item, R.layout.source_comfortable_grid_item, null -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)

        if (oldPosition != RecyclerView.NO_POSITION) {
            recycler.layoutManager?.scrollToPosition(oldPosition)
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.source_browse, menu)

        if (mode == Mode.RECOMMENDS) {
            menu.findItem(R.id.action_set_filter).isVisible = false
            menu.findItem(R.id.action_search).isVisible = false
        }

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE

        val query = presenter.query
        if (!query.isBlank()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }

        searchView.queryTextEvents()
            .filter { router.backstack.lastOrNull()?.controller() == this@BrowseSourceController }
            .filterIsInstance<QueryTextEvent.QuerySubmitted>()
            .onEach { searchWithQuery(it.queryText.toString()) }
            .launchIn(scope)

        searchItem.fixExpand(
            onExpand = { invalidateMenuOnExpand() },
            onCollapse = {
                searchWithQuery("")
                true
            }
        )

        // Setup filters button
        menu.findItem(R.id.action_set_filter).apply {
            icon.mutate()
            if (presenter.sourceFilters.isEmpty()) {
//                isEnabled = false [EXH]
                icon.alpha = 128
            } else {
//                isEnabled = true [EXH]
                icon.alpha = 255
            }
        }

        // Show next display mode
        val displayItem = when (preferences.catalogueDisplayMode().get()) {
            DisplayMode.COMPACT_GRID -> R.id.action_compact_grid
            DisplayMode.COMFORTABLE_GRID -> R.id.action_comfortable_grid
            DisplayMode.LIST -> R.id.action_list
        }
        menu.findItem(displayItem).isChecked = true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is HttpSource
        menu.findItem(R.id.action_open_in_web_view).isVisible = isHttpSource

        val isLocalSource = presenter.source is LocalSource
        menu.findItem(R.id.action_local_source_help).isVisible = isLocalSource && mode == Mode.CATALOGUE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_compact_grid -> setDisplayMode(DisplayMode.COMPACT_GRID)
            R.id.action_comfortable_grid -> setDisplayMode(DisplayMode.COMFORTABLE_GRID)
            R.id.action_list -> setDisplayMode(DisplayMode.LIST)
            R.id.action_set_filter -> navView?.let { activity?.drawer?.openDrawer(GravityCompat.END) }
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity, source.baseUrl, source.id, presenter.source.name)
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalSource.HELP_URL)
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery) {
            return
        }

        showProgressBar()
        adapter?.clear()

        presenter.restartPager(newQuery)
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAddPage(page: Int, mangas: List<SourceItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        if (page == 1) {
            adapter.clear()
            resetProgressItem()
        }
        adapter.onLoadMoreComplete(mangas)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onAddPageError(error: Throwable) {
        XLog.w("> Failed to load next catalogue page!", error)

        if (mode == Mode.CATALOGUE) {
            XLog.w(
                "> (source.id: %s, source.name: %s)",
                presenter.source.id,
                presenter.source.name
            )
        } else {
            XLog.w("> Recommendations")
        }

        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = View.OnClickListener {
            // If not the first page, show bottom progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            Log.d("Adapter", "empty")
            val actions = emptyList<EmptyView.Action>().toMutableList()

            if (presenter.source is LocalSource && mode == Mode.CATALOGUE) {
                actions += EmptyView.Action(R.string.local_source_help_guide, View.OnClickListener { openLocalSourceHelpGuide() })
            } else {
                actions += EmptyView.Action(R.string.action_retry, retryAction)
            }

            if (presenter.source is HttpSource) {
                actions += EmptyView.Action(R.string.action_open_in_web_view, View.OnClickListener { openInWebView() })
            }

            binding.emptyView.show(message, actions)
        } else {
            snack = binding.catalogueView.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.action_retry, retryAction)
            }
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return binding.catalogueView.context.getString(R.string.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${binding.catalogueView.context.getString(R.string.http_error_hint)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the manga initialized
     */
    fun onMangaInitialized(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Sets the current display mode.
     *
     * @param mode the mode to change to
     */
    private fun setDisplayMode(mode: DisplayMode) {
        val view = view ?: return
        val adapter = adapter ?: return

        preferences.catalogueDisplayMode().set(mode)
        activity?.invalidateOptionsMenu()
        setupRecycler(view)

        // Initialize mangas if not on a metered connection
        if (!view.context.connectivityManager.isActiveNetworkMetered) {
            val mangas = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? SourceItem)?.manga
            }
            presenter.initializeMangas(mangas)
        }
    }

    /**
     * Returns a preference for the number of manga per row based on the current orientation.
     *
     * @return the preference.
     */
    private fun getColumnsPreferenceForCurrentOrientation(): Preference<Int> {
        return if (resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            preferences.portraitColumns()
        } else {
            preferences.landscapeColumns()
        }
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): SourceHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition) as? SourceItem
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as SourceHolder
            }
        }

        return null
    }

    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.hide()
        binding.progress.visible()
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.hide()
        binding.progress.gone()
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false

        when (mode) {
            Mode.CATALOGUE -> router.pushController(
                MangaController(
                    item.manga,
                    true,
                    args.getParcelable(SMART_SEARCH_CONFIG_KEY)
                ).withFadeTransaction()
            )
            Mode.RECOMMENDS -> openSmartSearch(item.manga.title)
        }
        return false
    }

    // AZ -->
    private fun openSmartSearch(title: String) {
        val smartSearchConfig = SourceController.SmartSearchConfig(title)
        router.pushController(
            SourceController(
                Bundle().apply {
                    putParcelable(SourceController.SMART_SEARCH_CONFIG, smartSearchConfig)
                }
            ).withFadeTransaction()
        )
    }

    // AZ <--
    /**
     * Called when a manga is long clicked.
     *
     * Adds the manga to the default category if none is set it shows a list of categories for the user to put the manga
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new manga, and on already favorited manga the manga's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        if (mode == Mode.RECOMMENDS) { return }
        val activity = activity ?: return
        val manga = (adapter?.getItem(position) as? SourceItem?)?.manga ?: return

        if (manga.favorite) {
            MaterialDialog(activity)
                .listItems(
                    items = listOf(activity.getString(R.string.remove_from_library)),
                    waitForPositiveButton = false
                ) { _, which, _ ->
                    when (which) {
                        0 -> {
                            presenter.changeMangaFavorite(manga)
                            adapter?.notifyItemChanged(position)
                            activity.toast(activity.getString(R.string.manga_removed_library))
                        }
                    }
                }
                .show()
        } else {
            val categories = presenter.getCategories()
            val defaultCategoryId = preferences.defaultCategory()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    presenter.moveMangaToCategory(manga, defaultCategory)

                    presenter.changeMangaFavorite(manga)
                    adapter?.notifyItemChanged(position)
                    activity.toast(activity.getString(R.string.manga_added_library))
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    presenter.moveMangaToCategory(manga, null)

                    presenter.changeMangaFavorite(manga)
                    adapter?.notifyItemChanged(position)
                    activity.toast(activity.getString(R.string.manga_added_library))
                }

                // Choose a category
                else -> {
                    val ids = presenter.getMangaCategoryIds(manga)
                    val preselected = ids.mapNotNull { id ->
                        categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
                    }.toTypedArray()

                    ChangeMangaCategoriesDialog(this, listOf(manga), categories, preselected)
                        .showDialog(router)
                }
            }
        }
    }

    /**
     * Update manga to use selected categories.
     *
     * @param mangas The list of manga to move to categories.
     * @param categories The list of categories where manga will be placed.
     */
    override fun updateCategoriesForMangas(mangas: List<Manga>, categories: List<Category>) {
        val manga = mangas.firstOrNull() ?: return

        presenter.changeMangaFavorite(manga)
        presenter.updateMangaCategories(manga, categories)

        val position = adapter?.currentItems?.indexOfFirst { it -> (it as SourceItem).manga.id == manga.id }
        if (position != null) {
            adapter?.notifyItemChanged(position)
        }
        activity?.toast(activity?.getString(R.string.manga_added_library))
    }

    @Parcelize
    data class RecommendsConfig(val manga: Manga) : Parcelable

    enum class Mode {
        CATALOGUE,
        RECOMMENDS
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"

        const val SEARCH_QUERY_KEY = "searchQuery"
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"

        const val RECOMMENDS_CONFIG = "RECOMMENDS_CONFIG"
    }
}
