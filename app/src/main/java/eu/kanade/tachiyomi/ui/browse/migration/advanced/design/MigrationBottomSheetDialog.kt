package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy

@Composable
fun MigrationBottomSheetDialog(
    onDismissRequest: () -> Unit,
    onStartMigration: (extraParam: String?) -> Unit,
) {
    val startMigration = rememberUpdatedState(onStartMigration)
    val state = remember {
        MigrationBottomSheetDialogState(startMigration)
    }

    // KMK -->
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val textHighlightColor = MaterialTheme.colorScheme.inversePrimary.toArgb()
    // KMK <--

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        AndroidView(
            factory = { factoryContext ->
                val binding = MigrationBottomSheetBinding.inflate(LayoutInflater.from(factoryContext))
                state.initPreferences(binding)
                // KMK -->
                binding.migrateBtn.setBackgroundColor(primaryColor)
                binding.dataLabel.setTextColor(primaryColor)
                binding.optionsLabel.setTextColor(primaryColor)

                val buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked),
                    ),
                    intArrayOf(
                        primaryColor,
                        onSurface,
                    ),
                )

                binding.migChapters.buttonTintList = buttonTintList
                binding.migCategories.buttonTintList = buttonTintList
                binding.migTracking.buttonTintList = buttonTintList
                binding.migCustomCover.buttonTintList = buttonTintList
                binding.migExtra.buttonTintList = buttonTintList
                binding.migDeleteDownloaded.buttonTintList = buttonTintList

                binding.radioButton.buttonTintList = buttonTintList
                binding.radioButton2.buttonTintList = buttonTintList

                val trackTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked),
                    ),
                    intArrayOf(
                        primaryColor,
                        surface,
                    ),
                )

                binding.useSmartSearch.trackTintList = trackTintList
                binding.extraSearchParam.trackTintList = trackTintList
                binding.skipStep.trackTintList = trackTintList
                binding.HideNotFoundManga.trackTintList = trackTintList
                binding.OnlyShowUpdates.trackTintList = trackTintList

                val editTextBackgroundTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(-android.R.attr.state_focused),
                    ),
                    intArrayOf(
                        primaryColor,
                        onSurface,
                    ),
                )

                with(binding.extraSearchParamText) {
                    highlightColor = textHighlightColor
                    backgroundTintList = editTextBackgroundTintList

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textCursorDrawable = ColorDrawable(primaryColor)
                        textSelectHandle?.let { drawable ->
                            drawable.setTint(primaryColor)
                            setTextSelectHandle(drawable)
                        }
                        textSelectHandleLeft?.let { drawable ->
                            drawable.setTint(primaryColor)
                            setTextSelectHandleLeft(drawable)
                        }
                        textSelectHandleRight?.let { drawable ->
                            drawable.setTint(primaryColor)
                            setTextSelectHandleRight(drawable)
                        }
                    }
                }
                // KMK <--
                binding.root
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

class MigrationBottomSheetDialogState(private val onStartMigration: State<(extraParam: String?) -> Unit>) {
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
        binding.migDeleteDownloaded.isChecked = MigrationFlags.hasDeleteChapters(flags)

        binding.migChapters.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migCategories.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migTracking.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migCustomCover.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migExtra.setOnCheckedChangeListener { _, _ -> setFlags(binding) }
        binding.migDeleteDownloaded.setOnCheckedChangeListener { _, _ -> setFlags(binding) }

        binding.useSmartSearch.bindToPreference(preferences.smartMigration())
        binding.extraSearchParamText.isVisible = false
        binding.extraSearchParam.setOnCheckedChangeListener { _, isChecked ->
            binding.extraSearchParamText.isVisible = isChecked
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
                if (binding.useSmartSearch.isChecked && binding.extraSearchParamText.text.isNotBlank()) {
                    binding.extraSearchParamText.toString()
                } else {
                    null
                },
            )
        }
    }

    private fun setFlags(binding: MigrationBottomSheetBinding) {
        var flags = 0
        if (binding.migChapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (binding.migCategories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (binding.migTracking.isChecked) flags = flags or MigrationFlags.TRACK
        if (binding.migCustomCover.isChecked) flags = flags or MigrationFlags.CUSTOM_COVER
        if (binding.migExtra.isChecked) flags = flags or MigrationFlags.EXTRA
        if (binding.migDeleteDownloaded.isChecked) flags = flags or MigrationFlags.DELETE_CHAPTERS
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
