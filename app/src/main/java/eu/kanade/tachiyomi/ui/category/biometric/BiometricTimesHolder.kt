package eu.kanade.tachiyomi.ui.category.biometric

import android.view.View
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlin.time.ExperimentalTime

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class BiometricTimesHolder(view: View, val adapter: BiometricTimesAdapter) : FlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)

    /**
     * Binds this holder with the given category.
     *
     * @param timeRange The category to bind.
     */
    @OptIn(ExperimentalTime::class)
    fun bind(timeRange: TimeRange) {
        binding.innerContainer.minimumHeight = 60.dpToPx

        // Set capitalized title.
        binding.title.text = timeRange.getFormattedString(itemView.context)
        binding.reorder.isVisible = false
    }
}
