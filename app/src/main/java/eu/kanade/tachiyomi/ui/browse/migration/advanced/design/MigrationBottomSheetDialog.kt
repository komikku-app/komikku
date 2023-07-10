package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.getElevation
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.displayCompat
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.isTabletUi
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setNavigationBarTransparentCompat
import tachiyomi.core.preference.Preference
import tachiyomi.core.util.lang.toLong
import tachiyomi.domain.UnsortedPreferences
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(private val baseContext: Context, private val listener: StartMigrationListener) : BottomSheetDialog(baseContext) {
    private val preferences: UnsortedPreferences by injectLazy()

    lateinit var binding: MigrationBottomSheetBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = createView()
        setContentView(rootView)

        // Enforce max width for tablets
        if (context.isTabletUi()) {
            behavior.maxWidth = 480.dpToPx
        } else {
            behavior.maxWidth = 0.dpToPx
        }

        // Set peek height to 50% display height
        context.displayCompat?.let {
            val metrics = DisplayMetrics()
            it.getRealMetrics(metrics)
            behavior.peekHeight = metrics.heightPixels / 2
        }

        // Set navbar color to transparent for edge-to-edge bottom sheet if we can use light navigation bar
        // TODO Replace deprecated systemUiVisibility when material-components uses new API to modify status bar icons
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window?.setNavigationBarTransparentCompat(context, behavior.getElevation())
            val bottomSheet = rootView.parent as ViewGroup
            var flags = bottomSheet.systemUiVisibility
            flags = if (context.isNightMode()) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            bottomSheet.systemUiVisibility = flags
        }

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

    fun createView(): View {
        binding = MigrationBottomSheetBinding.inflate(LayoutInflater.from(baseContext))
        return binding.root
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

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
