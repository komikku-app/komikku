package eu.kanade.tachiyomi.ui.category.sources

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.categories_item.reorder
import kotlinx.android.synthetic.main.categories_item.title

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class SourceCategoryHolder(view: View, val adapter: SourceCategoryAdapter) : BaseFlexibleViewHolder(view, adapter) {
    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(category: String) {
        // Set capitalized title.
        title.text = category
        reorder.isVisible = false
    }
}
