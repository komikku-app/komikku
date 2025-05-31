package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
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
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.util.system.toast
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.toLong
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
        // Wrap AndroidView in a scrollable Column using verticalScroll
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { factoryContext ->
                    val binding = MigrationBottomSheetBinding.inflate(LayoutInflater.from(factoryContext))
                    state.initPreferences(binding)
                    // KMK -->
                    with(binding) {
                        migrateBtn.setBackgroundColor(colorScheme.primary)
                        dataLabel.setTextColor(colorScheme.primary)
                        optionsLabel.setTextColor(colorScheme.primary)

                        listOf(
                            migChapters,
                            migCategories,
                            migTracking,
                            migCustomCover,
                            migExtra,
                            migDeleteDownloaded,
                            radioButton,
                            radioButton2,
                        ).forEach {
                            it.buttonTintList = colorScheme.checkboxTintList
                        }

                        listOf(
                            useSmartSearch,
                            extraSearchParam,
                            skipStep,
                            hideNotFoundManga,
                            onlyShowUpdates,
                        ).forEach {
                            it.trackTintList = colorScheme.trackTintList
                            it.thumbTintList = colorScheme.thumbTintList
                        }

                        colorScheme.setTextInputLayoutColor(extraSearchParamInputLayout)
                        colorScheme.setEditTextColor(extraSearchParamText)
                    }
                    // KMK <--
                    binding.root
                },
            )
        }
    }
}

class MigrationBottomSheetDialogState(
    private val onStartMigration: State<(extraParam: String?) -> Unit>,
    // KMK -->
    private val fullSettings: Boolean = true,
    // KMK <--
) {
    private val preferences: SourcePreferences by injectLazy()

    /**
     * Init general reader preferences.
     */
    fun initPreferences(binding: MigrationBottomSheetBinding) {
        val flags = preferences.migrationFlags().get()

        with(binding) {
            migChapters.isChecked = MigrationFlag.CHAPTER in flags
            migCategories.isChecked = MigrationFlag.CATEGORY in flags
            migTracking.isChecked = MigrationFlag.TRACK in flags
            migCustomCover.isChecked = MigrationFlag.CUSTOM_COVER in flags
            migExtra.isChecked = MigrationFlag.EXTRA in flags
            migDeleteDownloaded.isChecked = MigrationFlag.REMOVE_DOWNLOAD in flags

            listOf(
                migChapters,
                migCategories,
                migTracking,
                migCustomCover,
                migExtra,
                migDeleteDownloaded,
            ).forEach { checkBox ->
                checkBox.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
            }

            useSmartSearch.bindToPreference(preferences.smartMigration())
            extraSearchParamInputLayout.isVisible = false
            extraSearchParam.setOnCheckedChangeListener { _, isChecked ->
                extraSearchParamInputLayout.isVisible = isChecked
            }
            sourceGroup.bindToPreference(preferences.useSourceWithMost())

            skipStep.isChecked = preferences.skipPreMigration().get()
            hideNotFoundManga.isChecked = preferences.hideNotFoundMigration().get()
            onlyShowUpdates.isChecked = preferences.showOnlyUpdatesMigration().get()
            skipStep.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    root.context.toast(
                        SYMR.strings.pre_migration_skip_toast,
                        Toast.LENGTH_LONG,
                    )
                }
            }

            migrateBtn.setOnClickListener {
                preferences.skipPreMigration().set(skipStep.isChecked)
                preferences.hideNotFoundMigration().set(hideNotFoundManga.isChecked)
                preferences.showOnlyUpdatesMigration().set(onlyShowUpdates.isChecked)
                onStartMigration.value(
                    if (useSmartSearch.isChecked && !extraSearchParamText.text.isNullOrBlank()) {
                        extraSearchParamText.text.toString()
                    } else {
                        null
                    },
                )
            }

            // KMK -->
            if (!fullSettings) {
                useSmartSearch.isVisible = false
                extraSearchParam.isVisible = false
                extraSearchParamInputLayout.isVisible = false
                sourceGroup.isVisible = false
                skipStep.isVisible = false
                migrateBtn.text = root.context.stringResource(MR.strings.action_save)
            }
            // KMK <--
        }
    }

    private fun setFlags(binding: MigrationBottomSheetBinding) {
        val flags = mutableSetOf<MigrationFlag>()
        with(binding) {
            if (migChapters.isChecked) flags.add(MigrationFlag.CHAPTER)
            if (migCategories.isChecked) flags.add(MigrationFlag.CATEGORY)
            if (migTracking.isChecked) flags.add(MigrationFlag.TRACK)
            if (migCustomCover.isChecked) flags.add(MigrationFlag.CUSTOM_COVER)
            if (migExtra.isChecked) flags.add(MigrationFlag.EXTRA)
            if (migDeleteDownloaded.isChecked) flags.add(MigrationFlag.REMOVE_DOWNLOAD)
        }
        preferences.migrationFlags().set(flags)
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
