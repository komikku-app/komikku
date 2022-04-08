package eu.kanade.tachiyomi.ui.browse.source.feed

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga

class SourceFeedCardItem(val manga: Manga) : AbstractFlexibleItem<SourceFeedCardHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.global_search_controller_card_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): SourceFeedCardHolder {
        return SourceFeedCardHolder(view, adapter as SourceFeedCardAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceFeedCardHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (other is SourceFeedCardItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id?.toInt() ?: 0
    }
}
