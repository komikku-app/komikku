package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class SavedSearchesItem(val chips: List<Chip>) :
    AbstractFlexibleItem<SavedSearchesHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.source_filter_sheet_saved_searches
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun isSwipeable(): Boolean {
        return false
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): SavedSearchesHolder {
        return SavedSearchesHolder(view, adapter as FlexibleAdapter<SavedSearchesItem>)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SavedSearchesHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.setChips(chips)
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
    }

    override fun hashCode(): Int {
        return this.hashCode()
    }
}
