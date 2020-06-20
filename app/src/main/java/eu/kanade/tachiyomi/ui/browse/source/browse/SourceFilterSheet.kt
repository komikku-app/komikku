package eu.kanade.tachiyomi.ui.browse.source.browse

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.MergeAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import exh.EXHSavedSearch
import kotlinx.android.synthetic.main.source_filter_sheet.view.filter_btn
import kotlinx.android.synthetic.main.source_filter_sheet.view.reset_btn
import kotlinx.android.synthetic.main.source_filter_sheet.view.save_search_btn

class SourceFilterSheet(
    activity: Activity,
    onFilterClicked: () -> Unit,
    onResetClicked: () -> Unit,
    // EXH -->
    onSaveClicked: () -> Unit,
    var onSavedSearchClicked: (Int) -> Unit = {},
    var onSavedSearchDeleteClicked: (Int, String) -> Unit = { _, _ -> }
    // EXH <--
) : BottomSheetDialog(activity) {

    private var filterNavView: FilterNavigationView

    init {
        filterNavView = FilterNavigationView(activity)
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

        setContentView(filterNavView)
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

    class FilterNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        SimpleNavigationView(context, attrs) {

        var onFilterClicked = {}
        var onResetClicked = {}

        // SY -->
        var onSaveClicked = {}

        var onSavedSearchClicked: (Int) -> Unit = {}

        var onSavedSearchDeleteClicked: (Int, String) -> Unit = { _, _ -> }

        var savedSearchesItem = SavedSearchesItem()
        var savedSearchesAdapter: FlexibleAdapter<SavedSearchesItem> = FlexibleAdapter<SavedSearchesItem>(listOf(savedSearchesItem))
        // SY <--

        val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)
            .setStickyHeaders(true)

        init {
            // SY -->
            recycler.adapter = MergeAdapter(savedSearchesAdapter, adapter)
            // SY <--
            recycler.setHasFixedSize(true)
            val view = inflate(R.layout.source_filter_sheet)
            ((view as ViewGroup).getChildAt(1) as ViewGroup).addView(recycler)
            addView(view)
            // SY -->
            save_search_btn.setOnClickListener { onSaveClicked() }
            // SY <--
            filter_btn.setOnClickListener { onFilterClicked() }
            reset_btn.setOnClickListener { onResetClicked() }
        }

        // EXH -->
        fun setSavedSearches(searches: List<EXHSavedSearch>) {
            save_search_btn.visibility = if (searches.size < MAX_SAVED_SEARCHES) View.VISIBLE else View.GONE
            val chips: MutableList<Chip> = mutableListOf()

            searches.withIndex().sortedBy { it.value.name.toLowerCase() }.forEach { (index, search) ->
                val chip = Chip(context).apply {
                    text = search.name
                    setOnClickListener { onSavedSearchClicked(index) }
                    setOnLongClickListener {
                        onSavedSearchDeleteClicked(index, search.name); true
                    }
                }

                chips += chip
            }
            savedSearchesAdapter.recyclerView.post {
                (recycler.findViewHolderForAdapterPosition(0) as? SavedSearchesHolder)?.setChips(chips)
                savedSearchesAdapter.expand(0)
            }
        }

        fun hideFilterButton() {
            filter_btn.gone()
        }

        companion object {
            const val MAX_SAVED_SEARCHES = 500 // if you want more than this, fuck you, i guess
        }
        // EXH <--
    }
}
