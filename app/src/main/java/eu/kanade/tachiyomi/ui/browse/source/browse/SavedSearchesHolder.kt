package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import com.google.android.material.chip.Chip
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.databinding.SourceFilterSheetSavedSearchesBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import timber.log.Timber

class SavedSearchesHolder(
    view: View,
    adapter: FlexibleAdapter<SavedSearchesItem>
) : BaseFlexibleViewHolder(view, adapter) {

    var binding: SourceFilterSheetSavedSearchesBinding = SourceFilterSheetSavedSearchesBinding.bind(itemView)

    fun setChips(chips: List<Chip> = emptyList()) {
        Timber.d("Chips set")
        binding.savedSearches.removeAllViews()
        if (chips.isEmpty()) {
            binding.savedSearchesTitle.gone()
        } else {
            binding.savedSearchesTitle.visible()
        }
        chips.forEach {
            Timber.d(it.text.toString())
            binding.savedSearches.addView(it)
        }
    }
}
