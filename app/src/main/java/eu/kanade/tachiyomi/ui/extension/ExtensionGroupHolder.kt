package eu.kanade.tachiyomi.ui.extension

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.extension_card_header.title
import kotlinx.android.synthetic.main.extension_card_header.action_button

class ExtensionGroupHolder(view: View, adapter: FlexibleAdapter<*>) :
    BaseFlexibleViewHolder(view, adapter) {

    @SuppressLint("SetTextI18n")
    fun bind(item: ExtensionGroupItem) {
        var text = item.name
        if (item.showSize) {
            text += " (${item.size})"
        }

        title.text = text

        action_button.isVisible = item.actionLabel != null && item.actionOnClick != null
        action_button.text = item.actionLabel
        action_button.setOnClickListener(if (item.actionLabel != null) item.actionOnClick else null)
    }
}
