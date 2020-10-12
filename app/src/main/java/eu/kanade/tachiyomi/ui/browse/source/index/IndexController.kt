package eu.kanade.tachiyomi.ui.browse.source.index

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.LatestControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterSheet
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.util.nullIfBlank
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.buildJsonObject
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [IndexPresenter]
 * [IndexCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class IndexController :
    NucleusController<LatestControllerBinding, IndexPresenter>,
    FabController,
    IndexCardAdapter.OnMangaClickListener,
    IndexAdapter.ClickListener {

    constructor(source: CatalogueSource?) : super(
        Bundle().apply {
            putLong(SOURCE_EXTRA, source?.id ?: 0)
        }
    ) {
        this.source = source
    }

    constructor(sourceId: Long) : this(
        Injekt.get<SourceManager>().get(sourceId) as? CatalogueSource
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(SOURCE_EXTRA))

    var source: CatalogueSource? = null

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: IndexAdapter? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Sheet containing filter items.
     */
    private var filterSheet: SourceFilterSheet? = null

    init {
        setHasOptionsMenu(true)
    }

    /**
     * Initiate the view with [R.layout.latest_controller].
     *
     * @param inflater used to load the layout xml.
     * @param container containing parent views.
     * @return inflated view
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = LatestControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun getTitle(): String? {
        return source!!.name
    }

    /**
     * Create the [IndexPresenter] used in controller.
     *
     * @return instance of [IndexPresenter]
     */
    override fun createPresenter(): IndexPresenter {
        return IndexPresenter(source!!)
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        router.pushController(MangaController(manga, true).withFadeTransaction())
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
        // Inflate menu.
        inflater.inflate(R.menu.global_search, menu)

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
            .filter { router.backstack.lastOrNull()?.controller() == this@IndexController }
            .onEach {
                if (it is QueryTextEvent.QueryChanged) {
                    presenter.query = it.queryText.toString()
                } else if (it is QueryTextEvent.QuerySubmitted) {
                    onBrowseClick(presenter.query.nullIfBlank())
                }
            }
            .launchIn(scope)

        searchItem.fixExpand(
            onExpand = { invalidateMenuOnExpand() }
        )
    }

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Prepare filter sheet
        initFilterSheet()

        adapter = IndexAdapter(this)

        // Create recycler and set adapter.
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter

        presenter.getLatest()
    }

    private val filterSerializer = FilterSerializer()

    open fun initFilterSheet() {
        if (presenter.sourceFilters.isEmpty()) {
            actionFab?.text = activity!!.getString(R.string.saved_searches)
        }

        filterSheet = SourceFilterSheet(
            activity!!,
            // SY -->
            this,
            presenter.source,
            presenter.loadSearches(),
            // SY <--
            onFilterClicked = {
                val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                filterSheet?.dismiss()
                if (!allDefault) {
                    val json = buildJsonObject { put("filters", filterSerializer.serialize(presenter.sourceFilters)) }
                    onBrowseClick(presenter.query.nullIfBlank(), json.toString())
                }
            },
            onResetClicked = {},
            onSaveClicked = {},
            onSavedSearchClicked = cb@{ indexToSearch ->
                val savedSearches = presenter.loadSearches()

                val search = savedSearches.getOrNull(indexToSearch)

                if (search == null) {
                    filterSheet?.context?.let {
                        MaterialDialog(it)
                            .title(R.string.save_search_failed_to_load)
                            .message(R.string.save_search_failed_to_load_message)
                            .cancelable(true)
                            .cancelOnTouchOutside(true)
                            .show()
                    }
                    return@cb
                }

                presenter.sourceFilters = FilterList(search.filterList)
                filterSheet?.setFilters(presenter.filterItems)
                val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                filterSheet?.dismiss()

                if (!allDefault) {
                    val json = buildJsonObject { put("filters", filterSerializer.serialize(presenter.sourceFilters)) }
                    onBrowseClick(presenter.query.nullIfBlank(), json.toString())
                }
            },
            onSavedSearchDeleteClicked = { _, _ -> }
        )
        filterSheet?.setFilters(presenter.filterItems)

        // TODO: [ExtendedFloatingActionButton] hide/show methods don't work properly
        filterSheet?.setOnShowListener { actionFab?.isVisible = false }
        filterSheet?.setOnDismissListener { actionFab?.isVisible = true }

        actionFab?.clicks()
            ?.onEach { filterSheet?.show() }
            ?.launchIn(scope)

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
        actionFabScrollListener?.let { binding.recycler.removeOnScrollListener(it) }
        actionFab = null
    }

    fun setLatestManga(results: List<IndexCardItem>?) {
        adapter?.holder?.bindLatest(results)
    }

    fun setBrowseManga(results: List<IndexCardItem>?) {
        adapter?.holder?.bindBrowse(results)
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onBrowseClick(search: String?, filters: String?) {
        router.replaceTopController(BrowseSourceController(presenter.source, search, filterList = filters).withFadeTransaction())
    }

    override fun onLatestClick() {
        router.replaceTopController(LatestUpdatesController(presenter.source).withFadeTransaction())
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(manga: Manga, isLatest: Boolean) {
        if (isLatest) adapter?.holder?.setLatestImage(manga)
        else adapter?.holder?.setBrowseImage(manga)
    }

    companion object {
        const val SOURCE_EXTRA = "source"
    }
}
