package eu.kanade.tachiyomi.ui.browse.source.feed

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

/**
 * Item that contains search result information.
 *
 * @param feed the source for the search results.
 * @param results the search results.
 * @param highlighted whether this search item should be highlighted/marked in the catalogue search view.
 */
class SourceFeedItem(
    val sourceFeed: SourceFeed,
    val results: List<SourceFeedCardItem>?,
    val highlighted: Boolean = false,
) : AbstractFlexibleItem<SourceFeedHolder>() {

    /**
     * Set view.
     *
     * @return id of view
     */
    override fun getLayoutRes(): Int {
        return R.layout.global_search_controller_card
    }

    /**
     * Create view holder (see [SourceFeedAdapter].
     *
     * @return holder of view.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): SourceFeedHolder {
        return SourceFeedHolder(view, adapter as SourceFeedAdapter)
    }

    /**
     * Bind item to view.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceFeedHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(this)
    }

    /**
     * Used to check if two items are equal.
     *
     * @return items are equal?
     */
    override fun equals(other: Any?): Boolean {
        if (other is SourceFeedItem) {
            return sourceFeed == other.sourceFeed
        }
        return false
    }

    /**
     * Return hash code of item.
     *
     * @return hashcode
     */
    override fun hashCode(): Int {
        return sourceFeed.hashCode()
    }
}
