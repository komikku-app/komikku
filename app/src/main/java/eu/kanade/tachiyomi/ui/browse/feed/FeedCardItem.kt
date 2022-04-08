package eu.kanade.tachiyomi.ui.browse.feed

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga

class FeedCardItem(val manga: Manga) : AbstractFlexibleItem<FeedCardHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.global_search_controller_card_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): FeedCardHolder {
        return FeedCardHolder(view, adapter as FeedCardAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: FeedCardHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (other is FeedCardItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id?.toInt() ?: 0
    }
}
