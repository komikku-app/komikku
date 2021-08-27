package eu.kanade.tachiyomi.ui.manga.info

import android.view.View
import com.google.android.material.chip.Chip
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.MangaInfoGenreGroupingBinding
import exh.util.makeSearchChip

class NamespaceTagsHolder(
    view: View,
    val adapter: NamespaceTagsAdapter
) : FlexibleViewHolder(view, adapter) {
    val binding = MangaInfoGenreGroupingBinding.bind(view)

    fun bind(item: NamespaceTagsItem) {
        binding.namespace.removeAllViews()
        val namespace = item.namespace
        if (namespace != null) {
            binding.namespace.addView(
                Chip(binding.root.context).apply {
                    text = namespace
                }
            )
        }

        binding.tags.removeAllViews()
        item.tags.map { (tag, type) ->
            binding.root.context.makeSearchChip(
                tag,
                adapter.controller::performSearch,
                adapter.controller::performGlobalSearch,
                adapter.source.id,
                namespace,
                type
            )
        }.forEach {
            binding.tags.addView(it)
        }
    }
}
