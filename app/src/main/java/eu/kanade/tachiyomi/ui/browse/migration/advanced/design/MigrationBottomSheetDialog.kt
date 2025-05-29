package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy

@Composable
fun MigrationBottomSheetDialog(
    onDismissRequest: () -> Unit,
    onStartMigration: (extraParam: String?) -> Unit,
    // KMK -->
    fullSettings: Boolean = true,
    // KMK <--
) {
    val startMigration = rememberUpdatedState(onStartMigration)
    val state = remember {
        MigrationBottomSheetDialogState(
            startMigration,
            // KMK -->
            fullSettings,
            // KMK <--
        )
    }

    // KMK -->
    val colorScheme = AndroidViewColorScheme(MaterialTheme.colorScheme)
    // KMK <--

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Wrap AndroidView in a scrollable Column using verticalScroll
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AndroidView(
                    factory = { factoryContext ->
                        val binding = MigrationBottomSheetBinding.inflate(LayoutInflater.from(factoryContext))
                        state.initPreferences(binding)
                        // KMK -->
                        binding.migrateBtn.setBackgroundColor(colorScheme.primary)
                        binding.dataLabel.setTextColor(colorScheme.primary)
                        binding.optionsLabel.setTextColor(colorScheme.primary)

                        binding.migChapters.buttonTintList = colorScheme.checkboxTintList
                        binding.migCategories.buttonTintList = colorScheme.checkboxTintList
                        binding.migTracking.buttonTintList = colorScheme.checkboxTintList
                        binding.migCustomCover.buttonTintList = colorScheme.checkboxTintList
                        binding.migExtra.buttonTintList = colorScheme.checkboxTintList
                        binding.migDeleteDownloaded.buttonTintList = colorScheme.checkboxTintList

                        binding.radioButton.buttonTintList = colorScheme.checkboxTintList
                        binding.radioButton2.buttonTintList = colorScheme.checkboxTintList

                        binding.useSmartSearch.trackTintList = colorScheme.trackTintList
                        binding.extraSearchParam.trackTintList = colorScheme.trackTintList
                        binding.skipStep.trackTintList = colorScheme.trackTintList
                        binding.HideNotFoundManga.trackTintList = colorScheme.trackTintList
                        binding.OnlyShowUpdates.trackTintList = colorScheme.trackTintList

                        binding.useSmartSearch.thumbTintList = colorScheme.thumbTintList
                        binding.extraSearchParam.thumbTintList = colorScheme.thumbTintList
                        binding.skipStep.thumbTintList = colorScheme.thumbTintList
                        binding.HideNotFoundManga.thumbTintList = colorScheme.thumbTintList
                        binding.OnlyShowUpdates.thumbTintList = colorScheme.thumbTintList

                        colorScheme.setTextInputLayoutColor(binding.extraSearchParamInputLayout)
                        colorScheme.setEditTextColor(binding.extraSearchParamText)
                        // KMK <--
                        binding.root
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

class MigrationBottomSheetDialogState(
    private val onStartMigration: State<(extraParam: String?) -> Unit>,
    // KMK -->
    private val fullSettings: Boolean = true,
    // KMK <--
) {
    private val preferences: UnsortedPreferences by injectLazy()

    /**
     * Init general reader preferences.
     */
    fun initPreferences(binding: MigrationBottomSheetBinding) {
        val flags = preferences.migrateFlags().get()

        binding.migChapters.isChecked = MigrationFlags.hasChapters(flags)
        binding.migCategories.isChecked = MigrationFlags.hasCategories(flags)
        binding.migTracking.isChecked = MigrationFlags.hasTracks(flags)
        binding.migCustomCover.isChecked = MigrationFlags.hasCustomCover(flags)
        binding.migExtra.isChecked = MigrationFlags.hasExtra(flags)
        binding.migDeleteDownloaded.isChecked = MigrationFlags.hasDeleteDownloaded(flags)

        binding.migChapters.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migCategories.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migTracking.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migCustomCover.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migExtra.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migDeleteDownloaded.setOnCheckedChangeListener { _, _ -> setFlags(binding) }

        binding.useSmartSearch.bindToPreference(preferences.smartMigration())
        binding.extraSearchParamInputLayout.isVisible = false
        binding.extraSearchParam.setOnCheckedChangeListener { _, isChecked ->
            binding.extraSearchParamInputLayout.isVisible = isChecked
        }
        binding.sourceGroup.bindToPreference(preferences.useSourceWithMost())

        binding.skipStep.isChecked = preferences.skipPreMigration().get()
        binding.HideNotFoundManga.isChecked = preferences.hideNotFoundMigration().get()
        binding.OnlyShowUpdates.isChecked = preferences.showOnlyUpdatesMigration().get()
        binding.skipStep.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.root.context.toast(
                    SYMR.strings.pre_migration_skip_toast,
                    Toast.LENGTH_LONG,
                )
            }
        }

        binding.migrateBtn.setOnClickListener {
            preferences.skipPreMigration().set(binding.skipStep.isChecked)
            preferences.hideNotFoundMigration().set(binding.HideNotFoundManga.isChecked)
            preferences.showOnlyUpdatesMigration().set(binding.OnlyShowUpdates.isChecked)
            onStartMigration.value(
                if (binding.useSmartSearch.isChecked && !binding.extraSearchParamText.text.isNullOrBlank()) {
                    binding.extraSearchParamText.text.toString()
                } else {
                    null
                },
            )
        }

        // KMK -->
        if (!fullSettings) {
            binding.useSmartSearch.isVisible = false
            binding.extraSearchParam.isVisible = false
            binding.extraSearchParamInputLayout.isVisible = false
            binding.sourceGroup.isVisible = false
            binding.skipStep.isVisible = false
            binding.migrateBtn.text = binding.root.context.stringResource(MR.strings.action_save)
        }
        // KMK <--
    }

    private fun setFlags(binding: MigrationBottomSheetBinding) {
        var flags = 0
        if (binding.migChapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (binding.migCategories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (binding.migTracking.isChecked) flags = flags or MigrationFlags.TRACK
        if (binding.migCustomCover.isChecked) flags = flags or MigrationFlags.CUSTOM_COVER
        if (binding.migExtra.isChecked) flags = flags or MigrationFlags.EXTRA
        if (binding.migDeleteDownloaded.isChecked) flags = flags or MigrationFlags.DELETE_DOWNLOADED
        preferences.migrateFlags().set(flags)
    }

    /**
     * Binds a checkbox or switch view with a boolean preference.
     */
    private fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
        isChecked = pref.get()
        setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    /**
     * Binds a radio group with a boolean preference.
     */
    private fun RadioGroup.bindToPreference(pref: Preference<Boolean>) {
        (getChildAt(pref.get().toLong().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }
}
