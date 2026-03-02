package exh.recs.batch

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.databinding.RecommendationSearchBottomSheetBinding
import uy.kohesive.injekt.injectLazy

@Composable
fun RecommendationSearchBottomSheetDialog(
    onDismissRequest: () -> Unit,
    onSearchRequest: () -> Unit,
) {
    val state = remember { RecommendationSearchBottomSheetDialogState(onSearchRequest) }
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        AndroidView(
            factory = { factoryContext ->
                val binding = RecommendationSearchBottomSheetBinding.inflate(LayoutInflater.from(factoryContext))
                state.initPreferences(binding)
                binding.root
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

class RecommendationSearchBottomSheetDialogState(private val onSearchRequest: () -> Unit) {
    private val preferences: SourcePreferences by injectLazy()

    fun initPreferences(binding: RecommendationSearchBottomSheetBinding) {
        val flags = preferences.recommendationSearchFlags().get()

        binding.recSources.isChecked = SearchFlags.hasIncludeSources(flags)
        binding.recTrackers.isChecked = SearchFlags.hasIncludeTrackers(flags)
        binding.recHideLibraryEntries.isChecked = SearchFlags.hasHideLibraryResults(flags)

        binding.recSources.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.recTrackers.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.recHideLibraryEntries.setOnCheckedChangeListener { _, _ -> setFlags(binding) }

        binding.recSearchBtn.setOnClickListener { _ -> onSearchRequest() }

        validate(binding)
    }

    private fun setFlags(binding: RecommendationSearchBottomSheetBinding) {
        var flags = 0
        if (binding.recSources.isChecked) flags = flags or SearchFlags.INCLUDE_SOURCES
        if (binding.recTrackers.isChecked) flags = flags or SearchFlags.INCLUDE_TRACKERS
        if (binding.recHideLibraryEntries.isChecked) flags = flags or SearchFlags.HIDE_LIBRARY_RESULTS
        preferences.recommendationSearchFlags().set(flags)

        validate(binding)
    }

    private fun validate(binding: RecommendationSearchBottomSheetBinding) {
        // Only enable search button if at least one of the checkboxes is checked
        binding.recSearchBtn.isEnabled = binding.recSources.isChecked || binding.recTrackers.isChecked
    }
}
