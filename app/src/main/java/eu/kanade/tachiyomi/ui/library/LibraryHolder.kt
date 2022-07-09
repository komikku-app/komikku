package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.viewbinding.ViewBinding
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.viewholders.FlexibleViewHolder

/**
 * Generic class used to hold the displayed data of a manga in the library.
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to the single tap and long tap events.
 */

abstract class LibraryHolder<VB : ViewBinding>(
    view: View,
    val adapter: FlexibleAdapter<*>,
) : FlexibleViewHolder(view, adapter) {

    abstract val binding: VB

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    abstract fun onSetValues(item: LibraryItem)

    // SY -->
    override fun onLongClick(view: View?): Boolean {
        return if (adapter.isLongPressDragEnabled) {
            super.onLongClick(view)
            false
        } else super.onLongClick(view)
    }
    // SY <--
}
