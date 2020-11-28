package eu.kanade.tachiyomi.ui.category.genre

import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class SortTagHolder(view: View, val adapter: SortTagAdapter) : FlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)

    init {
        setDragHandleView(binding.reorder)
    }

    /**
     * Binds this holder with the given category.
     *
     * @param tag The tag to bind.
     */
    fun bind(tag: String) {
        binding.title.text = tag
    }

    /**
     * Called when an item is released.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.onItemReleaseListener.onItemReleased(position)
    }
}
