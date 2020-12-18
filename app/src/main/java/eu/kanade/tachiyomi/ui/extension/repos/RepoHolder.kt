package eu.kanade.tachiyomi.ui.extension.repos

import android.view.View
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding

/**
 * Holder used to display repo items.
 *
 * @param view The view used by repo items.
 * @param adapter The adapter containing this holder.
 */
class RepoHolder(view: View, val adapter: RepoAdapter) : FlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)

    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(category: String) {
        // Set capitalized title.
        binding.title.text = category
        binding.reorder.isVisible = false
    }
}
