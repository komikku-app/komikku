package eu.kanade.tachiyomi.ui.setting.eh

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ScrollView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.EhDialogCategoriesBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.setting.SettingsEhController
import uy.kohesive.injekt.injectLazy

class FrontPageCategoriesDialog(
    bundle: Bundle? = null,
) : DialogController(bundle) {

    var binding: EhDialogCategoriesBinding? = null
        private set

    val preferences: PreferencesHelper by injectLazy()

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EhDialogCategoriesBinding.inflate(LayoutInflater.from(activity!!))
        val view = ScrollView(binding!!.root.context).apply {
            addView(binding!!.root)
        }
        onViewCreated()
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.language_filtering)
            .setMessage(R.string.language_filtering_summary)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onPositive()
            }
            .setOnDismissListener {
                onPositive()
            }
            .setOnCancelListener {
                onPositive()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    fun onViewCreated() {
        with(binding!!) {
            val list = preferences.exhEnabledCategories().get().split(",").map { !it.toBoolean() }
            doujinshiCheckbox.isChecked = list[0]
            mangaCheckbox.isChecked = list[1]
            artistCgCheckbox.isChecked = list[2]
            gameCgCheckbox.isChecked = list[3]
            westernCheckbox.isChecked = list[4]
            nonHCheckbox.isChecked = list[5]
            imageSetCheckbox.isChecked = list[6]
            cosplayCheckbox.isChecked = list[7]
            asianPornCheckbox.isChecked = list[8]
            miscCheckbox.isChecked = list[9]
        }
    }

    fun onPositive() {
        with(binding!!) {
            preferences.exhEnabledCategories().set(
                listOf(
                    (!doujinshiCheckbox.isChecked),
                    (!mangaCheckbox.isChecked),
                    (!artistCgCheckbox.isChecked),
                    (!gameCgCheckbox.isChecked),
                    (!westernCheckbox.isChecked),
                    (!nonHCheckbox.isChecked),
                    (!imageSetCheckbox.isChecked),
                    (!cosplayCheckbox.isChecked),
                    (!asianPornCheckbox.isChecked),
                    (!miscCheckbox.isChecked),
                ).joinToString(separator = ",") { it.toString() },
            )
        }
        with(targetController as? SettingsEhController ?: return) {
            preferences.exhSettingsLanguages().reconfigure()
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isEnter) {
            binding = null
        }
    }
}
