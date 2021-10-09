package eu.kanade.tachiyomi.ui.manga.info

import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.MangaInfoGenreGroupingBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import exh.util.makeSearchChip

class NamespaceTagsHolder(
    view: View,
    adapter: FlexibleAdapter<*>
) : FlexibleViewHolder(view, adapter) {
    val binding = MangaInfoGenreGroupingBinding.bind(view)

    fun bind(item: NamespaceTagsItem) {
        binding.namespace.removeAllViews()
        val namespace = item.namespace
        binding.namespace.isVisible = if (namespace != null) {
            binding.namespace.addView(
                Chip(binding.root.context).apply {
                    text = namespace
                }
            )
            binding.tags.updateLayoutParams<LinearLayout.LayoutParams> {
                marginStart = 8.dpToPx
            }
            true
        } else {
            binding.tags.updateLayoutParams<LinearLayout.LayoutParams> {
                marginStart = 16.dpToPx
            }
            false
        }

        binding.tags.removeAllViews()
        item.tags.map { (tag, type) ->
            binding.root.context.makeSearchChip(
                tag,
                item.onClick,
                item.onLongClick,
                item.source.id,
                namespace,
                type
            )
        }.forEach {
            binding.tags.addView(it)
        }
    }
}
