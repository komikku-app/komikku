package eu.kanade.tachiyomi.ui.manga.merged

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EditMergedSettingsItemBinding
import exh.merged.sql.models.MergedMangaReference

class EditMergedMangaItem(val mergedManga: Manga?, val mergedMangaReference: MergedMangaReference) : AbstractFlexibleItem<EditMergedMangaHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.edit_merged_settings_item
    }

    override fun isDraggable(): Boolean {
        return true
    }

    lateinit var binding: EditMergedSettingsItemBinding

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): EditMergedMangaHolder {
        binding = EditMergedSettingsItemBinding.bind(view)
        return EditMergedMangaHolder(binding.root, adapter as EditMergedMangaAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?,
        holder: EditMergedMangaHolder,
        position: Int,
        payloads: MutableList<Any>?,
    ) {
        holder.bind(this)
    }

    override fun hashCode(): Int {
        return mergedMangaReference.id!!.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is EditMergedMangaItem) {
            return mergedMangaReference.id!! == other.mergedMangaReference.id!!
        }
        return false
    }
}
