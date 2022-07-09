package eu.kanade.tachiyomi.ui.category.biometric

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

/**
 * Category item for a recycler view.
 */
class BiometricTimesItem(val timeRange: TimeRange) : AbstractFlexibleItem<BiometricTimesHolder>() {

    /**
     * Whether this item is currently selected.
     */
    var isSelected = false

    /**
     * Returns the layout resource for this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.categories_item
    }

    /**
     * Returns a new view holder for this item.
     *
     * @param view The view of this item.
     * @param adapter The adapter of this item.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): BiometricTimesHolder {
        return BiometricTimesHolder(view, adapter as BiometricTimesAdapter)
    }

    /**
     * Binds the given view holder with this item.
     *
     * @param adapter The adapter of this item.
     * @param holder The holder to bind.
     * @param position The position of this item in the adapter.
     * @param payloads List of partial changes.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: BiometricTimesHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(timeRange)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return false
    }

    override fun hashCode(): Int {
        return timeRange.hashCode()
    }
}
