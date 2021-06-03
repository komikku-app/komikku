package eu.kanade.tachiyomi.ui.browse.source.index

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.IndexControllerBinding
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
import eu.kanade.tachiyomi.util.system.toast
import exh.savedsearches.JsonSavedSearch
import exh.util.nullIfBlank
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [IndexPresenter]
 * [IndexCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class IndexController :
    SearchableNucleusController<IndexControllerBinding, IndexPresenter>,
    FabController,
    IndexCardAdapter.OnMangaClickListener {

    constructor(source: CatalogueSource?) : super(
        bundleOf(
            SOURCE_EXTRA to (source?.id ?: 0)
        )
    ) {
        this.source = source
    }

    constructor(sourceId: Long) : this(
        Injekt.get<SourceManager>().get(sourceId) as? CatalogueSource
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(SOURCE_EXTRA))

    var source: CatalogueSource? = null

    private var latestAdapter: IndexCardAdapter? = null
    private var browseAdapter: IndexCardAdapter? = null

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
        createOptionsMenu(menu, inflater, R.menu.global_search, R.id.action_search)
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        onBrowseClick(query.nullIfBlank())
    }

    override fun onSearchViewQueryTextChange(newText: String?) {
        if (router.backstack.lastOrNull()?.controller() == this) {
            presenter.query = newText ?: ""
        }
    }

    override fun createBinding(inflater: LayoutInflater) = IndexControllerBinding.inflate(inflater)

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Prepare filter sheet
        initFilterSheet()

        latestAdapter = IndexCardAdapter(this)

        binding.latestRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.latestRecycler.adapter = latestAdapter

        browseAdapter = IndexCardAdapter(this)

        binding.browseRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.browseRecycler.adapter = browseAdapter

        binding.latestBarWrapper.clicks()
            .onEach {
                onLatestClick()
            }
            .launchIn(viewScope)

        binding.browseBarWrapper.clicks()
            .onEach {
                onBrowseClick()
            }
            .launchIn(viewScope)

        presenter.latestItems
            .onEach {
                bind(it, true)
            }
            .launchIn(viewScope)

        presenter.browseItems
            .onEach {
                bind(it, false)
            }
            .launchIn(viewScope)

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
                    val json = Json.encodeToString(
                        JsonSavedSearch(
                            "",
                            "",
                            filterSerializer.serialize(presenter.sourceFilters)
                        )
                    )
                    onBrowseClick(presenter.query.nullIfBlank(), json)
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

                if (search.filterList == null) {
                    activity?.toast(R.string.save_search_invalid)
                    return@cb
                }

                presenter.sourceFilters = FilterList(search.filterList)
                filterSheet?.setFilters(presenter.filterItems)
                val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                filterSheet?.dismiss()

                if (!allDefault) {
                    val json = Json.encodeToString(
                        JsonSavedSearch(
                            "",
                            "",
                            filterSerializer.serialize(presenter.sourceFilters)
                        )
                    )
                    onBrowseClick(presenter.query.nullIfBlank(), json)
                }
            },
            onSavedSearchDeleteClicked = { _, _ -> }
        )
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

    private fun bind(results: List<IndexCardItem>?, isLatest: Boolean) {
        val progress = if (isLatest) binding.latestProgress else binding.browseProgress
        when {
            results == null -> {
                progress.isVisible = true
                showResultsHolder(isLatest)
            }
            results.isEmpty() -> {
                progress.isVisible = false
                showNoResults(isLatest)
            }
            else -> {
                progress.isVisible = false
                showResultsHolder(isLatest)
            }
        }

        val adapter = if (isLatest) {
            latestAdapter
        } else {
            browseAdapter
        }
        adapter?.updateDataSet(results)
    }

    fun onError(e: Exception, isLatest: Boolean) {
        e.message?.let {
            val textView = if (isLatest) {
                binding.latestNoResultsFound
            } else {
                binding.browseNoResultsFound
            }
            textView.text = it
        }
    }

    private fun showResultsHolder(isLatest: Boolean) {
        (if (isLatest) binding.latestNoResultsFound else binding.browseNoResultsFound).isVisible = false
    }

    private fun showNoResults(isLatest: Boolean) {
        (if (isLatest) binding.latestNoResultsFound else binding.browseNoResultsFound).isVisible = true
    }

    override fun onDestroyView(view: View) {
        latestAdapter = null
        browseAdapter = null
        super.onDestroyView(view)
    }

    fun onBrowseClick(search: String? = null, filters: String? = null) {
        router.replaceTopController(BrowseSourceController(presenter.source, search, filterList = filters).withFadeTransaction())
    }

    private fun onLatestClick() {
        router.replaceTopController(LatestUpdatesController(presenter.source).withFadeTransaction())
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(manga: Manga, isLatest: Boolean) {
        val adapter = if (isLatest) latestAdapter else browseAdapter
        adapter ?: return

        adapter.allBoundViewHolders.forEach {
            if (it !is IndexCardHolder) return@forEach
            if (adapter.getItem(it.bindingAdapterPosition)?.manga?.id != manga.id) return@forEach
            it.setImage(manga)
        }
    }

    companion object {
        const val SOURCE_EXTRA = "source"
    }
}
