package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog
import tachiyomi.core.preference.Preference
import tachiyomi.core.util.lang.toLong
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(private val baseContext: Context, private val listener: StartMigrationListener) : BaseBottomSheetDialog(baseContext) {
    private val preferences: UnsortedPreferences by injectLazy()

    lateinit var binding: MigrationBottomSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = MigrationBottomSheetBinding.inflate(LayoutInflater.from(baseContext))
        return binding.root
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPreferences()

        binding.migrateBtn.setOnClickListener {
            preferences.skipPreMigration().set(binding.skipStep.isChecked)
            preferences.hideNotFoundMigration().set(binding.HideNotFoundManga.isChecked)
            listener.startMigration(
                if (binding.useSmartSearch.isChecked && binding.extraSearchParamText.text.isNotBlank()) {
                    binding.extraSearchParamText.toString()
                } else {
                    null
                },
            )
            dismiss()
        }

        behavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    /**
     * Init general reader preferences.
     */
    private fun initPreferences() {
        val flags = preferences.migrateFlags().get()

        binding.migChapters.isChecked = MigrationFlags.hasChapters(flags)
        binding.migCategories.isChecked = MigrationFlags.hasCategories(flags)
        binding.migTracking.isChecked = MigrationFlags.hasTracks(flags)
        binding.migCustomCover.isChecked = MigrationFlags.hasCustomCover(flags)
        binding.migExtra.isChecked = MigrationFlags.hasExtra(flags)

        binding.migChapters.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migCategories.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migTracking.setOnCheckedChangeListener { _, _ -> setFlags() }
        binding.migCustomCover.setOnCheckedChangeListener { _, _ -> setFlags() }
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
                context.toast(
                    R.string.pre_migration_skip_toast,
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    private fun setFlags() {
        var flags = 0
        if (binding.migChapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (binding.migCategories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (binding.migTracking.isChecked) flags = flags or MigrationFlags.TRACK
        if (binding.migCustomCover.isChecked) flags = flags or MigrationFlags.CUSTOM_COVER
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
        (getChildAt(pref.get().toLong().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }
}

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
