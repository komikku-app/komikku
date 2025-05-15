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
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
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
        val flags = preferences.migrateFlags().get()

        with(binding) {
            migChapters.isChecked = MigrationFlags.hasChapters(flags)
            migCategories.isChecked = MigrationFlags.hasCategories(flags)
            migTracking.isChecked = MigrationFlags.hasTracks(flags)
            migCustomCover.isChecked = MigrationFlags.hasCustomCover(flags)
            migExtra.isChecked = MigrationFlags.hasExtra(flags)
            migDeleteDownloaded.isChecked = MigrationFlags.hasDeleteDownloaded(flags)

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
        var flags = 0
        with(binding) {
            @Suppress("KotlinConstantConditions")
            if (migChapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
            if (migCategories.isChecked) flags = flags or MigrationFlags.CATEGORIES
            if (migTracking.isChecked) flags = flags or MigrationFlags.TRACK
            if (migCustomCover.isChecked) flags = flags or MigrationFlags.CUSTOM_COVER
            if (migExtra.isChecked) flags = flags or MigrationFlags.EXTRA
            if (migDeleteDownloaded.isChecked) flags = flags or MigrationFlags.DELETE_DOWNLOADED
        }
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
