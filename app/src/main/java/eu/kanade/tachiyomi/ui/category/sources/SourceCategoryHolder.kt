package eu.kanade.tachiyomi.ui.category.sources

import android.view.View
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class SourceCategoryHolder(view: View, val adapter: SourceCategoryAdapter) : FlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)

    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(category: String) {
        binding.innerContainer.minimumHeight = 48.dpToPx

        // Set capitalized title.
        binding.title.text = category
        binding.reorder.isVisible = false
    }
}
