package eu.kanade.tachiyomi.ui.browse.source.browse

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceFilterSheetBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.BrowseSourceFilterHeader
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog
import exh.savedsearches.EXHSavedSearch
import exh.source.getMainSource
import exh.util.under

class SourceFilterSheet(
    activity: Activity,
    // SY -->
    controller: BaseController<*>,
    source: CatalogueSource,
    searches: List<EXHSavedSearch> = emptyList(),
    // SY <--
    private val onFilterClicked: () -> Unit,
    private val onResetClicked: () -> Unit,
    // EXH -->
    private val onSaveClicked: () -> Unit,
    var onSavedSearchClicked: (Int) -> Unit = {},
    var onSavedSearchDeleteClicked: (Int, String) -> Unit = { _, _ -> }
    // EXH <--
) : BaseBottomSheetDialog(activity) {

    private var filterNavView: FilterNavigationView = FilterNavigationView(
        activity,
        // SY -->
        searches = searches,
        source = source,
        controller = controller,
        dismissSheet = ::dismiss
        // SY <--
    )

    override fun createView(inflater: LayoutInflater): View {
        filterNavView.onFilterClicked = {
            onFilterClicked()
            this.dismiss()
        }
        filterNavView.onResetClicked = onResetClicked

        // EXH -->
        filterNavView.onSaveClicked = onSaveClicked

        filterNavView.onSavedSearchClicked = onSavedSearchClicked

        filterNavView.onSavedSearchDeleteClicked = onSavedSearchDeleteClicked
        // EXH <--

        return filterNavView
    }

    override fun show() {
        super.show()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun setFilters(items: List<IFlexible<*>>) {
        filterNavView.adapter.updateDataSet(items)
    }

    // SY -->
    fun setSavedSearches(searches: List<EXHSavedSearch>) {
        filterNavView.setSavedSearches(searches)
    }

    fun hideFilterButton() {
        filterNavView.hideFilterButton()
    }
    // SY <--

    class FilterNavigationView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        // SY -->
        searches: List<EXHSavedSearch> = emptyList(),
        source: CatalogueSource? = null,
        controller: BaseController<*>? = null,
        dismissSheet: (() -> Unit)? = null
        // SY <--
    ) :
        SimpleNavigationView(context, attrs) {

        var onFilterClicked = {}
        var onResetClicked = {}

        // SY -->
        var onSaveClicked = {}

        var onSavedSearchClicked: (Int) -> Unit = {}

        var onSavedSearchDeleteClicked: (Int, String) -> Unit = { _, _ -> }

        val adapters = mutableListOf<RecyclerView.Adapter<*>>()

        private val savedSearchesAdapter = SavedSearchesAdapter(getSavedSearchesChips(searches))
        // SY <--

        val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)

        private val binding = SourceFilterSheetBinding.inflate(
            LayoutInflater.from(context),
            null,
            false
        )

        init {
            // SY -->
            val mainSource = source?.getMainSource()
            if (mainSource is BrowseSourceFilterHeader && controller != null) {
                adapters += mainSource.getFilterHeader(controller) { dismissSheet?.invoke() }
            }
            adapters += savedSearchesAdapter
            adapters += adapter
            recycler.adapter = ConcatAdapter(adapters)
            // SY <--
            recycler.setHasFixedSize(true)
            (binding.root.getChildAt(1) as ViewGroup).addView(recycler)
            addView(binding.root)
            // SY -->
            binding.saveSearchBtn.setOnClickListener { onSaveClicked() }
            // SY <--
            binding.filterBtn.setOnClickListener { onFilterClicked() }
            binding.resetBtn.setOnClickListener { onResetClicked() }
        }

        // EXH -->
        fun setSavedSearches(searches: List<EXHSavedSearch>) {
            val savedSearchesChips = getSavedSearchesChips(searches)
            savedSearchesAdapter.chips = savedSearchesChips
            recycler.post {
                (recycler.findViewHolderForAdapterPosition(0) as? SavedSearchesAdapter.SavedSearchesViewHolder)?.bind(savedSearchesChips)
            }
        }

        private fun getSavedSearchesChips(searches: List<EXHSavedSearch>): List<Chip> {
            recycler.post {
                binding.saveSearchBtn.isVisible = searches.size under MAX_SAVED_SEARCHES
            }
            return searches.withIndex()
                .sortedBy { it.value.name }
                .map { (index, search) ->
                    Chip(context).apply {
                        text = search.name
                        setOnClickListener { onSavedSearchClicked(index) }
                        setOnLongClickListener {
                            onSavedSearchDeleteClicked(index, search.name); true
                        }
                    }
                }
                .sortedBy { it.text.toString().lowercase() }
        }

        fun hideFilterButton() {
            binding.filterBtn.isVisible = false
        }

        companion object {
            const val MAX_SAVED_SEARCHES = 500 // if you want more than this, fuck you, i guess
        }
        // EXH <--
    }
}
