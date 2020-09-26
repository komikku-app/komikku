package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.source_main_controller_card_item.image
import kotlinx.android.synthetic.main.source_main_controller_card_item.source_latest
import kotlinx.android.synthetic.main.source_main_controller_card_item.title

class SourceHolder(view: View, val adapter: SourceAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    // SY -->
    init {
        source_latest.isVisible = true
        source_latest.text = view.context.getString(R.string.all)
        source_latest.setOnClickListener {
            adapter.allClickListener?.onAllClick(bindingAdapterPosition)
        }
    }
    // SY <--

    fun bind(item: SourceItem) {
        val source = item.source

        // Set source name
        title.text = source.name

        // Set source icon
        itemView.post {
            image.setImageDrawable(source.icon())
        }
    }
}
