package eu.kanade.tachiyomi.ui.category.biometric

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.categories_item.reorder
import kotlinx.android.synthetic.main.categories_item.title
import kotlin.time.ExperimentalTime

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class BiometricTimesHolder(view: View, val adapter: BiometricTimesAdapter) : BaseFlexibleViewHolder(view, adapter) {
    /**
     * Binds this holder with the given category.
     *
     * @param timeRange The category to bind.
     */
    @OptIn(ExperimentalTime::class)
    fun bind(timeRange: TimeRange) {
        // Set capitalized title.
        title.text = timeRange.getFormattedString(itemView.context)
        reorder.isVisible = false
    }
}
