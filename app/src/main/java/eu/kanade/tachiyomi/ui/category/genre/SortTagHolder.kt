package eu.kanade.tachiyomi.ui.category.genre

import android.view.View
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.categories_item.title

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class SortTagHolder(view: View, val adapter: SortTagAdapter) : BaseFlexibleViewHolder(view, adapter) {
    /**
     * Binds this holder with the given category.
     *
     * @param tag The tag to bind.
     */
    fun bind(tag: String) {
        // Set capitalized title.
        title.text = tag
    }
}
