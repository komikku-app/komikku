package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.Controller
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(activity: Activity, private val listener: StartMigrationListener) : BaseBottomSheetDialog(activity) {
    private val preferences: PreferencesHelper by injectLazy()

    lateinit var binding: MigrationBottomSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = MigrationBottomSheetBinding.inflate(inflater)
        return binding.root
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPreferences()

        binding.fab.setOnClickListener {
            preferences.skipPreMigration().set(binding.skipStep.isChecked)
            preferences.hideNotFoundMigration().set(binding.HideNotFoundManga.isChecked)
            listener.startMigration(
                if (binding.useSmartSearch.isChecked && binding.extraSearchParamText.text.isNotBlank()) {
                    binding.extraSearchParamText.toString()
                } else null
            )
            dismiss()
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initPreferences() {
        val flags = preferences.migrateFlags().get()

        binding.migChapters.isChecked = MigrationFlags.hasChapters(flags)
        binding.migCategories.isChecked = MigrationFlags.hasCategories(flags)
        binding.migTracking.isChecked = MigrationFlags.hasTracks(flags)
        binding.migExtra.isChecked = MigrationFlags.hasExtra(flags)

        binding.migChapters.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migCategories.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migTracking.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migExtra.setOnCheckedChangeListener { _, _ -> setFlags() }

        binding.useSmartSearch.bindToPreference(preferences.smartMigration())
        binding.extraSearchParamText.isVisible = false
        binding.extraSearchParam.setOnCheckedChangeListener { _, isChecked ->
            binding.extraSearchParamText.isVisible = isChecked
        }
        binding.sourceGroup.bindToPreference(preferences.useSourceWithMost())

        binding.skipStep.isChecked = preferences.skipPreMigration().get()
        binding.HideNotFoundManga.isChecked = preferences.hideNotFoundMigration().get()
        binding.skipStep.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                (listener as? Controller)?.activity?.toast(
                    R.string.pre_migration_skip_toast,
                    Toast.LENGTH_LONG
                )
            }
        }
    }

    private fun setFlags() {
        var flags = 0
        if (binding.migChapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (binding.migCategories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (binding.migTracking.isChecked) flags = flags or MigrationFlags.TRACK
        if (binding.migExtra.isChecked) flags = flags or MigrationFlags.EXTRA
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
        (getChildAt(pref.get().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
