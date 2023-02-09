package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import cafe.adriel.voyager.navigator.Navigator
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceFilterSheetBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog
import exh.md.MangaDexFabHeaderAdapter
import exh.source.getMainSource
import tachiyomi.domain.source.model.EXHSavedSearch

class SourceFilterSheet(
    context: Context,
    // SY -->
    navigator: Navigator,
    source: CatalogueSource,
    searches: List<EXHSavedSearch> = emptyList(),
    // SY <--
    private val onFilterClicked: () -> Unit,
    private val onResetClicked: () -> Unit,
    // EXH -->
    private val onSaveClicked: () -> Unit,
    var onSavedSearchClicked: (Long) -> Unit = {},
    var onSavedSearchDeleteClicked: (Long, String) -> Unit = { _, _ -> },
    // EXH <--
) : BaseBottomSheetDialog(context) {

    private var filterNavView: FilterNavigationView = FilterNavigationView(
        context,
        // SY -->
        searches = searches,
        source = source,
        navigator = navigator,
        dismissSheet = ::dismiss,
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
        navigator: Navigator? = null,
        dismissSheet: (() -> Unit)? = null,
        // SY <--
    ) :
        SimpleNavigationView(context, attrs) {

        var onFilterClicked = {}
        var onResetClicked = {}

        // SY -->
        var onSaveClicked = {}

        var onSavedSearchClicked: (Long) -> Unit = {}

        var onSavedSearchDeleteClicked: (Long, String) -> Unit = { _, _ -> }

        private val savedSearchesAdapter = SavedSearchesAdapter(getSavedSearchesChips(searches))
        // SY <--

        val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)

        private val binding = SourceFilterSheetBinding.inflate(
            LayoutInflater.from(context),
            null,
            false,
        )

        init {
            // SY -->
            recycler.adapter = ConcatAdapter(
                listOfNotNull(
                    navigator?.let {
                        source?.getMainSource<MangaDex>()
                            ?.let {
                                MangaDexFabHeaderAdapter(navigator, it) {
                                    dismissSheet?.invoke()
                                }
                            }
                    },
                    savedSearchesAdapter,
                    adapter,
                ),
            )
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
            savedSearchesAdapter.chips = getSavedSearchesChips(searches)
            savedSearchesAdapter.notifyItemChanged(0)
        }

        private fun getSavedSearchesChips(searches: List<EXHSavedSearch>): List<Chip> {
            return searches
                .map { search ->
                    Chip(context).apply {
                        text = search.name
                        setOnClickListener { onSavedSearchClicked(search.id) }
                        setOnLongClickListener {
                            onSavedSearchDeleteClicked(search.id, search.name); true
                        }
                    }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.text.toString() })
        }

        fun hideFilterButton() {
            binding.filterBtn.isVisible = false
        }
        // EXH <--
    }
}
