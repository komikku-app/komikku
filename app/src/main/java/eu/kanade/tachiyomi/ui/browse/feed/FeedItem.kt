package eu.kanade.tachiyomi.ui.browse.feed

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch

/**
 * Item that contains search result information.
 *
 * @param feed the source for the search results.
 * @param results the search results.
 * @param highlighted whether this search item should be highlighted/marked in the catalogue search view.
 */
class FeedItem(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: CatalogueSource?,
    val results: List<FeedCardItem>?,
    val highlighted: Boolean = false,
) : AbstractFlexibleItem<FeedHolder>() {

    /**
     * Set view.
     *
     * @return id of view
     */
    override fun getLayoutRes(): Int {
        return R.layout.global_search_controller_card
    }

    /**
     * Create view holder (see [FeedAdapter].
     *
     * @return holder of view.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): FeedHolder {
        return FeedHolder(view, adapter as FeedAdapter)
    }

    /**
     * Bind item to view.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: FeedHolder,
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
        if (other is FeedItem) {
            return feed.id == other.feed.id
        }
        return false
    }

    /**
     * Return hash code of item.
     *
     * @return hashcode
     */
    override fun hashCode(): Int {
        return feed.id.toInt()
    }
}
