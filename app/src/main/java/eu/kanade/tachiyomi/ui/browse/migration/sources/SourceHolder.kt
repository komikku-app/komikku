package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceMainControllerCardItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

class SourceHolder(view: View, val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    val binding = SourceMainControllerCardItemBinding.bind(view)

    // SY -->
    init {
        binding.sourceLatest.isVisible = true
        binding.sourceLatest.text = view.context.getString(R.string.all)
        binding.sourceLatest.setOnClickListener {
            adapter.allClickListener?.onAllClick(bindingAdapterPosition)
        }
    }
    // SY <--

    fun bind(item: SourceItem) {
        val source = item.source

        // Set source name
        binding.title.text = source.name

        // Set source icon
        itemView.post {
            binding.image.setImageDrawable(source.icon())
        }
    }
}
