package eu.kanade.tachiyomi.ui.browse.source.browse

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import exh.EXHSavedSearch
import kotlinx.android.synthetic.main.source_filter_sheet.view.filter_btn
import kotlinx.android.synthetic.main.source_filter_sheet.view.reset_btn
import kotlinx.android.synthetic.main.source_filter_sheet.view.save_search_btn
import kotlinx.android.synthetic.main.source_filter_sheet.view.saved_searches
import kotlinx.android.synthetic.main.source_filter_sheet.view.saved_searches_title

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
        // EXH <--

        // EXH -->
        filterNavView.onSavedSearchClicked = onSavedSearchClicked
        // EXH <--

        // EXH -->
        filterNavView.onSavedSearchDeleteClicked = onSavedSearchDeleteClicked
        // EXH <--

        setContentView(filterNavView)
    }

    fun setFilters(items: List<IFlexible<*>>) {
        filterNavView.adapter.updateDataSet(items)
    }

    fun setSavedSearches(searches: List<EXHSavedSearch>) {
        filterNavView.setSavedSearches(searches)
    }

    fun hideFilterButton() {
        filterNavView.hideFilterButton()
    }

    class FilterNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        SimpleNavigationView(context, attrs) {

        // EXH -->
        var onSaveClicked = {}
        // EXH <--

        // EXH -->
        var onSavedSearchClicked: (Int) -> Unit = {}
        // EXH <--

        // EXH -->
        var onSavedSearchDeleteClicked: (Int, String) -> Unit = { _, _ -> }
        // EXH <--

        var onFilterClicked = {}
        var onResetClicked = {}

        val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)
            .setStickyHeaders(true)

        init {
            recycler.adapter = adapter
            recycler.setHasFixedSize(true)
            val view = inflate(R.layout.source_filter_sheet)
            ((view as ViewGroup).findViewById(R.id.source_filter_content) as ViewGroup).addView(recycler)
            addView(view)
            save_search_btn.setOnClickListener { onSaveClicked() }
            filter_btn.setOnClickListener { onFilterClicked() }
            reset_btn.setOnClickListener { onResetClicked() }
        }

        // EXH -->
        fun setSavedSearches(searches: List<EXHSavedSearch>) {
            saved_searches.removeAllViews()

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

            save_search_btn.visibility = if (searches.size < MAX_SAVED_SEARCHES) View.VISIBLE else View.GONE

            if (searches.isEmpty()) {
                saved_searches_title.gone()
            } else {
                saved_searches_title.visible()
            }

            searches.withIndex().sortedBy { it.value.name }.forEach { (index, search) ->
                val chip = Chip(context).apply {
                    text = search.name
                    setOnClickListener { onSavedSearchClicked(index) }
                    setOnLongClickListener {
                        onSavedSearchDeleteClicked(index, search.name); true
                    }
                }

                saved_searches.addView(chip)
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
