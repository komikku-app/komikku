package eu.kanade.tachiyomi.ui.manga.info

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R

open class NamespaceTagsItem(val namespace: String?, val tags: List<Chip>) : AbstractFlexibleItem<NamespaceTagsItem.Holder>() {

    override fun getLayoutRes(): Int {
        return R.layout.manga_info_genre_grouping
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: List<Any?>?) {
        val namespaceChip = Chip(holder.itemView.context)
        namespaceChip.text = namespace ?: holder.itemView.context.getString(R.string.unknown)
        holder.namespaceChipGroup.addView(namespaceChip)

        tags.forEach {
            holder.tagsChipGroup.addView(it)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return namespace == (other as NamespaceTagsItem).namespace
    }

    override fun hashCode(): Int {
        return namespace.hashCode()
    }

    class Holder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {
        val namespaceChipGroup: ChipGroup = itemView.findViewById(R.id.namespace)
        val tagsChipGroup: ChipGroup = itemView.findViewById(R.id.tags)
    }
}
