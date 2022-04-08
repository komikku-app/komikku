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
import eu.kanade.tachiyomi.databinding.EhDialogLanguagesBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.setting.SettingsEhController
import uy.kohesive.injekt.injectLazy

class LanguagesDialog(
    bundle: Bundle? = null,
) : DialogController(bundle) {

    var binding: EhDialogLanguagesBinding? = null
        private set

    val preferences: PreferencesHelper by injectLazy()

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EhDialogLanguagesBinding.inflate(LayoutInflater.from(activity!!))
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
        val settingsLanguages = preferences.exhSettingsLanguages().get().split("\n")

        val japanese = settingsLanguages[0].split("*").map { it.toBoolean() }
        val english = settingsLanguages[1].split("*").map { it.toBoolean() }
        val chinese = settingsLanguages[2].split("*").map { it.toBoolean() }
        val dutch = settingsLanguages[3].split("*").map { it.toBoolean() }
        val french = settingsLanguages[4].split("*").map { it.toBoolean() }
        val german = settingsLanguages[5].split("*").map { it.toBoolean() }
        val hungarian = settingsLanguages[6].split("*").map { it.toBoolean() }
        val italian = settingsLanguages[7].split("*").map { it.toBoolean() }
        val korean = settingsLanguages[8].split("*").map { it.toBoolean() }
        val polish = settingsLanguages[9].split("*").map { it.toBoolean() }
        val portuguese = settingsLanguages[10].split("*").map { it.toBoolean() }
        val russian = settingsLanguages[11].split("*").map { it.toBoolean() }
        val spanish = settingsLanguages[12].split("*").map { it.toBoolean() }
        val thai = settingsLanguages[13].split("*").map { it.toBoolean() }
        val vietnamese = settingsLanguages[14].split("*").map { it.toBoolean() }
        val notAvailable =
            settingsLanguages[15].split("*").map { it.toBoolean() }
        val other = settingsLanguages[16].split("*").map { it.toBoolean() }

        with(binding!!) {
            japaneseOriginal.isChecked = japanese[0]
            japaneseTranslated.isChecked = japanese[1]
            japaneseRewrite.isChecked = japanese[2]

            japaneseOriginal.isChecked = japanese[0]
            japaneseTranslated.isChecked = japanese[1]
            japaneseRewrite.isChecked = japanese[2]

            englishOriginal.isChecked = english[0]
            englishTranslated.isChecked = english[1]
            englishRewrite.isChecked = english[2]

            chineseOriginal.isChecked = chinese[0]
            chineseTranslated.isChecked = chinese[1]
            chineseRewrite.isChecked = chinese[2]

            dutchOriginal.isChecked = dutch[0]
            dutchTranslated.isChecked = dutch[1]
            dutchRewrite.isChecked = dutch[2]

            frenchOriginal.isChecked = french[0]
            frenchTranslated.isChecked = french[1]
            frenchRewrite.isChecked = french[2]

            germanOriginal.isChecked = german[0]
            germanTranslated.isChecked = german[1]
            germanRewrite.isChecked = german[2]

            hungarianOriginal.isChecked = hungarian[0]
            hungarianTranslated.isChecked = hungarian[1]
            hungarianRewrite.isChecked = hungarian[2]

            italianOriginal.isChecked = italian[0]
            italianTranslated.isChecked = italian[1]
            italianRewrite.isChecked = italian[2]

            koreanOriginal.isChecked = korean[0]
            koreanTranslated.isChecked = korean[1]
            koreanRewrite.isChecked = korean[2]

            polishOriginal.isChecked = polish[0]
            polishTranslated.isChecked = polish[1]
            polishRewrite.isChecked = polish[2]

            portugueseOriginal.isChecked = portuguese[0]
            portugueseTranslated.isChecked = portuguese[1]
            portugueseRewrite.isChecked = portuguese[2]

            russianOriginal.isChecked = russian[0]
            russianTranslated.isChecked = russian[1]
            russianRewrite.isChecked = russian[2]

            spanishOriginal.isChecked = spanish[0]
            spanishTranslated.isChecked = spanish[1]
            spanishRewrite.isChecked = spanish[2]

            thaiOriginal.isChecked = thai[0]
            thaiTranslated.isChecked = thai[1]
            thaiRewrite.isChecked = thai[2]

            vietnameseOriginal.isChecked = vietnamese[0]
            vietnameseTranslated.isChecked = vietnamese[1]
            vietnameseRewrite.isChecked = vietnamese[2]

            notAvailableOriginal.isChecked = notAvailable[0]
            notAvailableTranslated.isChecked = notAvailable[1]
            notAvailableRewrite.isChecked = notAvailable[2]

            otherOriginal.isChecked = other[0]
            otherTranslated.isChecked = other[1]
            otherRewrite.isChecked = other[2]
        }
    }

    fun onPositive() {
        val languages = with(binding!!) {
            listOf(
                "${japaneseOriginal.isChecked}*${japaneseTranslated.isChecked}*${japaneseRewrite.isChecked}",
                "${englishOriginal.isChecked}*${englishTranslated.isChecked}*${englishRewrite.isChecked}",
                "${chineseOriginal.isChecked}*${chineseTranslated.isChecked}*${chineseRewrite.isChecked}",
                "${dutchOriginal.isChecked}*${dutchTranslated.isChecked}*${dutchRewrite.isChecked}",
                "${frenchOriginal.isChecked}*${frenchTranslated.isChecked}*${frenchRewrite.isChecked}",
                "${germanOriginal.isChecked}*${germanTranslated.isChecked}*${germanRewrite.isChecked}",
                "${hungarianOriginal.isChecked}*${hungarianTranslated.isChecked}*${hungarianRewrite.isChecked}",
                "${italianOriginal.isChecked}*${italianTranslated.isChecked}*${italianRewrite.isChecked}",
                "${koreanOriginal.isChecked}*${koreanTranslated.isChecked}*${koreanRewrite.isChecked}",
                "${polishOriginal.isChecked}*${polishTranslated.isChecked}*${polishRewrite.isChecked}",
                "${portugueseOriginal.isChecked}*${portugueseTranslated.isChecked}*${portugueseRewrite.isChecked}",
                "${russianOriginal.isChecked}*${russianTranslated.isChecked}*${russianRewrite.isChecked}",
                "${spanishOriginal.isChecked}*${spanishTranslated.isChecked}*${spanishRewrite.isChecked}",
                "${thaiOriginal.isChecked}*${thaiTranslated.isChecked}*${thaiRewrite.isChecked}",
                "${vietnameseOriginal.isChecked}*${vietnameseTranslated.isChecked}*${vietnameseRewrite.isChecked}",
                "${notAvailableOriginal.isChecked}*${notAvailableTranslated.isChecked}*${notAvailableRewrite.isChecked}",
                "${otherOriginal.isChecked}*${otherTranslated.isChecked}*${otherRewrite.isChecked}",
            ).joinToString("\n")
        }

        preferences.exhSettingsLanguages().set(languages)

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
