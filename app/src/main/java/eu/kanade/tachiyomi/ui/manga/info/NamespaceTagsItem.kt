package eu.kanade.tachiyomi.ui.manga.info

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class NamespaceTagsItem(val namespace: String?, val tags: List<Pair<String, Int?>>) :
    AbstractFlexibleItem<NamespaceTagsHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.manga_info_genre_grouping
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): NamespaceTagsHolder {
        return NamespaceTagsHolder(view, adapter as NamespaceTagsAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: NamespaceTagsHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NamespaceTagsItem

        if (namespace != other.namespace) return false

        return true
    }

    override fun hashCode(): Int {
        return namespace.hashCode()
    }
}
