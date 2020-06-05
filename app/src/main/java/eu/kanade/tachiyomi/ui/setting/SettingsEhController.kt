package eu.kanade.tachiyomi.ui.setting

import android.os.Handler
import android.text.InputType
import android.widget.Toast
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.kizitonwose.time.Interval
import com.kizitonwose.time.days
import com.kizitonwose.time.hours
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.system.toast
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.eh.EHentaiUpdateWorker
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.EHentaiUpdaterStats
import exh.favorites.FavoritesIntroDialog
import exh.favorites.LocalFavoritesStorage
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.nullIfBlank
import exh.uconfig.WarnConfigureDialogController
import exh.ui.login.LoginController
import exh.util.await
import exh.util.trans
import humanize.Humanize
import java.util.Date
import kotlinx.android.synthetic.main.eh_dialog_categories.view.artist_cg_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.asian_porn_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.cosplay_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.doujinshi_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.game_cg_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.image_set_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.manga_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.misc_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.non_h_checkbox
import kotlinx.android.synthetic.main.eh_dialog_categories.view.western_checkbox
import kotlinx.android.synthetic.main.eh_dialog_languages.view.chinese_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.chinese_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.chinese_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.dutch_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.dutch_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.dutch_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.english_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.english_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.english_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.french_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.french_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.french_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.german_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.german_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.german_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.hungarian_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.hungarian_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.hungarian_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.italian_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.italian_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.italian_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.japanese_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.japanese_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.japanese_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.korean_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.korean_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.korean_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.not_available_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.not_available_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.not_available_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.other_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.other_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.other_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.polish_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.polish_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.polish_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.portuguese_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.portuguese_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.portuguese_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.russian_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.russian_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.russian_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.spanish_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.spanish_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.spanish_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.thai_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.thai_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.thai_translated
import kotlinx.android.synthetic.main.eh_dialog_languages.view.vietnamese_original
import kotlinx.android.synthetic.main.eh_dialog_languages.view.vietnamese_rewrite
import kotlinx.android.synthetic.main.eh_dialog_languages.view.vietnamese_translated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
    private val gson: Gson by injectLazy()
    private val db: DatabaseHelper by injectLazy()

    private fun Preference<*>.reconfigure(): Boolean {
        // Listen for change commit
        asFlow()
            .take(1) // Only listen for first commit
            .onEach {
                // Only listen for first change commit
                WarnConfigureDialogController.uploadSettings(router)
            }
            .launchIn(scope)

        // Always return true to save changes
        return true
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "E-Hentai"

        preferenceCategory {
            title = "E-Hentai Website Account Settings"

            switchPreference {
                title = "Enable ExHentai"
                summaryOff = "Requires login"
                key = PreferenceKeys.eh_enableExHentai
                isPersistent = false
                defaultValue = false
                preferences.enableExhentai()
                    .asFlow()
                    .onEach {
                        isChecked = it
                    }
                    .launchIn(scope)

                onChange { newVal ->
                    newVal as Boolean
                    if (!newVal) {
                        preferences.enableExhentai().set(false)
                        true
                    } else {
                        router.pushController(
                            RouterTransaction.with(LoginController())
                                .pushChangeHandler(FadeChangeHandler())
                                .popChangeHandler(FadeChangeHandler())
                        )
                        false
                    }
                }
            }

            intListPreference {
                title = "Use Hentai@Home Network"

                key = PreferenceKeys.eh_enable_hah
                if (preferences.eh_hathPerksCookies().get().isBlank()) {
                    summary = "Do you wish to load images through the Hentai@Home Network, if available? Disabling this option will reduce the amount of pages you are able to view\nOptions:\n - Any client (Recommended)\n - Default port clients only (Can be slower. Enable if behind firewall/proxy that blocks outgoing non-standard ports.)"
                    entries = arrayOf(
                        "Any client (Recommended)",
                        "Default port clients only"
                    )
                    entryValues = arrayOf("0", "1")
                } else {
                    summary = "Do you wish to load images through the Hentai@Home Network, if available? Disabling this option will reduce the amount of pages you are able to view\nOptions:\n - Any client (Recommended)\n - Default port clients only (Can be slower. Enable if behind firewall/proxy that blocks outgoing non-standard ports.)\n - No (Donator only. You will not be able to browse as many pages, enable only if having severe problems.)"
                    entries = arrayOf(
                        "Any client (Recommended)",
                        "Default port clients only",
                        "No(will select Default port clients only if you are not a donator)"
                    )
                    entryValues = arrayOf("0", "1", "2")
                }

                onChange { preferences.useHentaiAtHome().reconfigure() }
            }.dependency = PreferenceKeys.eh_enableExHentai

            switchPreference {
                title = "Show Japanese titles in search results"
                summaryOn = "Currently showing Japanese titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
                summaryOff = "Currently showing English/Romanized titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
                key = "use_jp_title"
                defaultValue = false

                onChange { preferences.useJapaneseTitle().reconfigure() }
            }.dependency = PreferenceKeys.eh_enableExHentai

            switchPreference {
                title = "Use original images"
                summaryOn = "Currently using original images"
                summaryOff = "Currently using resampled images"
                key = PreferenceKeys.eh_useOrigImages
                defaultValue = false

                onChange { preferences.eh_useOriginalImages().reconfigure() }
            }.dependency = PreferenceKeys.eh_enableExHentai

            preference {
                title = "Watched Tags"
                summary = "Opens a webview to your E/ExHentai watched tags page"
                onClick {
                    val intent = if (preferences.enableExhentai().get()) {
                        WebViewActivity.newIntent(activity!!, url = "https://exhentai.org/mytags", title = "ExHentai Watched Tags")
                    } else {
                        WebViewActivity.newIntent(activity!!, url = "https://e-hentai.org/mytags", title = "E-Hentai Watched Tags")
                    }
                    startActivity(intent)
                }
            }.dependency = PreferenceKeys.eh_enableExHentai

            preference {
                title = "Tag Filtering Threshold"
                key = PreferenceKeys.eh_tag_filtering_value
                defaultValue = 0

                summary = "You can soft filter tags by adding them to the \"My Tags\" E/ExHentai page with a negative weight. If a gallery has tags that add up to weight below this value, it is filtered from view. This threshold can be set between -9999 and 0. Currently: ${preferences.ehTagFilterValue().get()}"

                onClick {
                    MaterialDialog(activity!!)
                        .title(text = "Tag Filtering Threshold")
                        .input(
                            inputType = InputType.TYPE_NUMBER_FLAG_SIGNED,
                            waitForPositiveButton = false,
                            allowEmpty = false
                        ) { dialog, number ->
                            val inputField = dialog.getInputField()
                            val value = number.toString().toIntOrNull()

                            if (value != null && value in -9999..0) {
                                inputField.error = null
                            } else {
                                inputField.error = "Must be between -9999 and 0!"
                            }
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, value != null && value in -9999..0)
                        }
                        .positiveButton(android.R.string.ok) {
                            val value = it.getInputField().text.toString().toInt()
                            preferences.ehTagFilterValue().set(value)
                            summary = "You can soft filter tags by adding them to the \"My Tags\" E/ExHentai page with a negative weight. If a gallery has tags that add up to weight below this value, it is filtered from view. This threshold can be set between 0 and -9999. Currently: $value"
                            preferences.ehTagFilterValue().reconfigure()
                        }
                        .show()
                }
            }.dependency = PreferenceKeys.eh_enableExHentai

            preference {
                title = "Tag Watching Threshold"
                key = PreferenceKeys.eh_tag_watching_value
                defaultValue = 0

                summary = "Recently uploaded galleries will be included on the watched screen if it has at least one watched tag with positive weight, and the sum of weights on its watched tags add up to this value or higher. This threshold can be set between 0 and 9999. Currently: ${preferences.ehTagWatchingValue().get()}"

                onClick {
                    MaterialDialog(activity!!)
                        .title(text = "Tag Watching Threshold")
                        .input(
                            inputType = InputType.TYPE_NUMBER_FLAG_SIGNED,
                            maxLength = 4,
                            waitForPositiveButton = false,
                            allowEmpty = false
                        ) { dialog, number ->
                            val inputField = dialog.getInputField()
                            val value = number.toString().toIntOrNull()

                            if (value != null && value in 0..9999) {
                                inputField.error = null
                            } else {
                                inputField.error = "Must be between 0 and 9999!"
                            }
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, value != null && value in 0..9999)
                        }
                        .positiveButton(android.R.string.ok) {
                            val value = it.getInputField().text.toString().toInt()
                            preferences.ehTagWatchingValue().set(value)
                            summary = "Recently uploaded galleries will be included on the watched screen if it has at least one watched tag with positive weight, and the sum of weights on its watched tags add up to this value or higher. This threshold can be set between 0 and 9999. Currently: $value"
                            preferences.ehTagWatchingValue().reconfigure()
                        }
                        .show()
                }
            }.dependency = PreferenceKeys.eh_enableExHentai

            preference {
                title = "Language Filtering"
                summary = "If you wish to hide galleries in certain languages from the gallery list and searches, select them in the dialog that will popup.\nNote that matching galleries will never appear regardless of your search query.\nTldr checkmarked = exclude"

                onClick {
                    MaterialDialog(activity!!)
                        .title(text = "Language Filtering")
                        .message(text = "If you wish to hide galleries in certain languages from the gallery list and searches, select them in the dialog that will popup.\nNote that matching galleries will never appear regardless of your search query.\nTldr checkmarked = exclude")
                        .customView(R.layout.eh_dialog_languages, scrollable = true)
                        .positiveButton(android.R.string.ok) {
                            val customView = it.view.contentLayout.customView!!

                            val languages = with(customView) {
                                listOfNotNull(
                                    "${japanese_original.isChecked}*${japanese_translated.isChecked}*${japanese_rewrite.isChecked}",
                                    "${english_original.isChecked}*${english_translated.isChecked}*${english_rewrite.isChecked}",
                                    "${chinese_original.isChecked}*${chinese_translated.isChecked}*${chinese_rewrite.isChecked}",
                                    "${dutch_original.isChecked}*${dutch_translated.isChecked}*${dutch_rewrite.isChecked}",
                                    "${french_original.isChecked}*${french_translated.isChecked}*${french_rewrite.isChecked}",
                                    "${german_original.isChecked}*${german_translated.isChecked}*${german_rewrite.isChecked}",
                                    "${hungarian_original.isChecked}*${hungarian_translated.isChecked}*${hungarian_rewrite.isChecked}",
                                    "${italian_original.isChecked}*${italian_translated.isChecked}*${italian_rewrite.isChecked}",
                                    "${korean_original.isChecked}*${korean_translated.isChecked}*${korean_rewrite.isChecked}",
                                    "${polish_original.isChecked}*${polish_translated.isChecked}*${polish_rewrite.isChecked}",
                                    "${portuguese_original.isChecked}*${portuguese_translated.isChecked}*${portuguese_rewrite.isChecked}",
                                    "${russian_original.isChecked}*${russian_translated.isChecked}*${russian_rewrite.isChecked}",
                                    "${spanish_original.isChecked}*${spanish_translated.isChecked}*${spanish_rewrite.isChecked}",
                                    "${thai_original.isChecked}*${thai_translated.isChecked}*${thai_rewrite.isChecked}",
                                    "${vietnamese_original.isChecked}*${vietnamese_translated.isChecked}*${vietnamese_rewrite.isChecked}",
                                    "${not_available_original.isChecked}*${not_available_translated.isChecked}*${not_available_rewrite.isChecked}",
                                    "${other_original.isChecked}*${other_translated.isChecked}*${other_rewrite.isChecked}"
                                ).joinToString("\n")
                            }

                            preferences.eh_settingsLanguages().set(languages)

                            preferences.eh_settingsLanguages().reconfigure()
                        }
                        .show {
                            val customView = this.view.contentLayout.customView!!
                            val settingsLanguages = preferences.eh_settingsLanguages().get().split("\n")

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

                            with(customView) {
                                japanese_original.isChecked = japanese[0]
                                japanese_translated.isChecked = japanese[1]
                                japanese_rewrite.isChecked = japanese[2]

                                japanese_original.isChecked = japanese[0]
                                japanese_translated.isChecked = japanese[1]
                                japanese_rewrite.isChecked = japanese[2]

                                english_original.isChecked = english[0]
                                english_translated.isChecked = english[1]
                                english_rewrite.isChecked = english[2]

                                chinese_original.isChecked = chinese[0]
                                chinese_translated.isChecked = chinese[1]
                                chinese_rewrite.isChecked = chinese[2]

                                dutch_original.isChecked = dutch[0]
                                dutch_translated.isChecked = dutch[1]
                                dutch_rewrite.isChecked = dutch[2]

                                french_original.isChecked = french[0]
                                french_translated.isChecked = french[1]
                                french_rewrite.isChecked = french[2]

                                german_original.isChecked = german[0]
                                german_translated.isChecked = german[1]
                                german_rewrite.isChecked = german[2]

                                hungarian_original.isChecked = hungarian[0]
                                hungarian_translated.isChecked = hungarian[1]
                                hungarian_rewrite.isChecked = hungarian[2]

                                italian_original.isChecked = italian[0]
                                italian_translated.isChecked = italian[1]
                                italian_rewrite.isChecked = italian[2]

                                korean_original.isChecked = korean[0]
                                korean_translated.isChecked = korean[1]
                                korean_rewrite.isChecked = korean[2]

                                polish_original.isChecked = polish[0]
                                polish_translated.isChecked = polish[1]
                                polish_rewrite.isChecked = polish[2]

                                portuguese_original.isChecked = portuguese[0]
                                portuguese_translated.isChecked = portuguese[1]
                                portuguese_rewrite.isChecked = portuguese[2]

                                russian_original.isChecked = russian[0]
                                russian_translated.isChecked = russian[1]
                                russian_rewrite.isChecked = russian[2]

                                spanish_original.isChecked = spanish[0]
                                spanish_translated.isChecked = spanish[1]
                                spanish_rewrite.isChecked = spanish[2]

                                thai_original.isChecked = thai[0]
                                thai_translated.isChecked = thai[1]
                                thai_rewrite.isChecked = thai[2]

                                vietnamese_original.isChecked = vietnamese[0]
                                vietnamese_translated.isChecked = vietnamese[1]
                                vietnamese_rewrite.isChecked = vietnamese[2]

                                not_available_original.isChecked = notAvailable[0]
                                not_available_translated.isChecked = notAvailable[1]
                                not_available_rewrite.isChecked = notAvailable[2]

                                other_original.isChecked = other[0]
                                other_translated.isChecked = other[1]
                                other_rewrite.isChecked = other[2]
                            }
                        }
                }
            }.dependency = PreferenceKeys.eh_enableExHentai

            preference {
                title = "Front Page Categories"
                summary = "What categories would you like to show by default on the front page and in searches? They can still be enabled by enabling their filters"

                onClick {
                    MaterialDialog(activity!!)
                        .title(text = "Front Page Categories")
                        .message(text = "What categories would you like to show by default on the front page and in searches? They can still be enabled by enabling their filters")
                        .customView(R.layout.eh_dialog_categories, scrollable = true)
                        .positiveButton {
                            val customView = it.view.contentLayout.customView!!

                            with(customView) {
                                preferences.eh_EnabledCategories().set(
                                    listOf(
                                        (!doujinshi_checkbox.isChecked).toString(),
                                        (!manga_checkbox.isChecked).toString(),
                                        (!artist_cg_checkbox.isChecked).toString(),
                                        (!game_cg_checkbox.isChecked).toString(),
                                        (!western_checkbox.isChecked).toString(),
                                        (!non_h_checkbox.isChecked).toString(),
                                        (!image_set_checkbox.isChecked).toString(),
                                        (!cosplay_checkbox.isChecked).toString(),
                                        (!asian_porn_checkbox.isChecked).toString(),
                                        (!misc_checkbox.isChecked).toString()
                                    ).joinToString(",")
                                )
                            }

                            preferences.eh_EnabledCategories().reconfigure()
                        }
                        .show {
                            val customView = this.view.contentLayout.customView!!

                            with(customView) {
                                val list = preferences.eh_EnabledCategories().get().split(",").map { !it.toBoolean() }
                                doujinshi_checkbox.isChecked = list[0]
                                manga_checkbox.isChecked = list[1]
                                artist_cg_checkbox.isChecked = list[2]
                                game_cg_checkbox.isChecked = list[3]
                                western_checkbox.isChecked = list[4]
                                non_h_checkbox.isChecked = list[5]
                                image_set_checkbox.isChecked = list[6]
                                cosplay_checkbox.isChecked = list[7]
                                asian_porn_checkbox.isChecked = list[8]
                                misc_checkbox.isChecked = list[9]
                            }
                        }
                }
            }.dependency = PreferenceKeys.eh_enableExHentai

            switchPreference {
                defaultValue = false
                key = PreferenceKeys.eh_watched_list_default_state
                title = "Watched List Filter Default State"
                summary = "When browsing ExHentai/E-Hentai should the watched list filter be enabled by default"
            }

            switchPreference {
                defaultValue = true
                key = PreferenceKeys.eh_secure_exh
                title = "Secure ExHentai/E-Hentai"
                summary = "Use the HTTPS version of ExHentai/E-Hentai."
            }

            listPreference {
                defaultValue = "auto"
                key = PreferenceKeys.eh_ehentai_quality
                summary = "The quality of the downloaded images"
                title = "Image quality"
                entries = arrayOf(
                    "Auto",
                    "2400x",
                    "1600x",
                    "1280x",
                    "980x",
                    "780x"
                )
                entryValues = arrayOf(
                    "auto",
                    "ovrs_2400",
                    "ovrs_1600",
                    "high",
                    "med",
                    "low"
                )

                onChange { preferences.imageQuality().reconfigure() }
            }.dependency = PreferenceKeys.eh_enableExHentai
        }

        preferenceCategory {
            title = "Favorites sync"

            switchPreference {
                title = "Disable favorites uploading"
                summary = "Favorites are only downloaded from ExHentai. Any changes to favorites in the app will not be uploaded. Prevents accidental loss of favorites on ExHentai. Note that removals will still be downloaded (if you remove a favorites on ExHentai, it will be removed in the app as well)."
                key = PreferenceKeys.eh_readOnlySync
                defaultValue = false
            }

            preference {
                title = "Show favorites sync notes"
                summary = "Show some information regarding the favorites sync feature"

                onClick {
                    activity?.let {
                        FavoritesIntroDialog().show(it)
                    }
                }
            }

            switchPreference {
                title = "Ignore sync errors when possible"
                summary = "Do not abort immediately when encountering errors during the sync process. Errors will still be displayed when the sync is complete. Can cause loss of favorites in some cases. Useful when syncing large libraries."
                key = PreferenceKeys.eh_lenientSync
                defaultValue = false
            }

            preference {
                title = "Force sync state reset"
                summary = "Performs a full resynchronization on the next sync. Removals will not be synced. All favorites in the app will be re-uploaded to ExHentai and all favorites on ExHentai will be re-downloaded into the app. Useful for repairing sync after sync has been interrupted."

                onClick {
                    activity?.let { activity ->
                        MaterialDialog(activity)
                            .title(R.string.eh_force_sync_reset_title)
                            .message(R.string.eh_force_sync_reset_message)
                            .positiveButton(android.R.string.yes) {
                                LocalFavoritesStorage().apply {
                                    getRealm().use {
                                        it.trans {
                                            clearSnapshots(it)
                                        }
                                    }
                                }
                                activity.toast("Sync state reset", Toast.LENGTH_LONG)
                            }
                            .negativeButton(android.R.string.no)
                            .cancelable(false)
                            .show()
                    }
                }
            }
        }

        preferenceCategory {
            title = "Gallery update checker"

            intListPreference {
                key = PreferenceKeys.eh_autoUpdateFrequency
                title = "Time between update batches"
                entries = arrayOf(
                    "Never update galleries",
                    "1 hour",
                    "2 hours",
                    "3 hours",
                    "6 hours",
                    "12 hours",
                    "24 hours",
                    "48 hours"
                )
                entryValues = arrayOf("0", "1", "2", "3", "6", "12", "24", "48")
                defaultValue = "0"

                preferences.eh_autoUpdateFrequency().asFlow()
                    .onEach { newVal ->
                        summary = if (newVal == 0) {
                            "${context.getString(R.string.app_name)} will currently never check galleries in your library for updates."
                        } else {
                            "${context.getString(R.string.app_name)} checks/updates galleries in batches. " +
                                "This means it will wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} galleries," +
                                " wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} and so on..."
                        }
                    }
                    .launchIn(scope)

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    EHentaiUpdateWorker.scheduleBackground(context, interval)
                    true
                }
            }

            multiSelectListPreference {
                key = PreferenceKeys.eh_autoUpdateRestrictions
                title = "Auto update restrictions"
                entriesRes = arrayOf(R.string.wifi, R.string.charging)
                entryValues = arrayOf("wifi", "ac")
                summaryRes = R.string.pref_library_update_restriction_summary

                preferences.eh_autoUpdateFrequency().asFlow()
                    .onEach { isVisible = it > 0 }
                    .launchIn(scope)

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    Handler().post { EHentaiUpdateWorker.scheduleBackground(context) }
                    true
                }
            }

            preference {
                title = "Show updater statistics"

                onClick {
                    val progress = MaterialDialog(context)
                        .message(R.string.eh_show_update_statistics_dialog)
                        .cancelable(false)
                    progress.show()

                    GlobalScope.launch(Dispatchers.IO) {
                        val updateInfo = try {
                            val stats =
                                preferences.eh_autoUpdateStats().get().nullIfBlank()?.let {
                                    gson.fromJson<EHentaiUpdaterStats>(it)
                                }

                            val statsText = if (stats != null) {
                                "The updater last ran ${Humanize.naturalTime(Date(stats.startTime))}, and checked ${stats.updateCount} out of the ${stats.possibleUpdates} galleries that were ready for checking."
                            } else "The updater has not ran yet."

                            val allMeta = db.getFavoriteMangaWithMetadata().await().filter {
                                it.source == EH_SOURCE_ID || it.source == EXH_SOURCE_ID
                            }.mapNotNull {
                                db.getFlatMetadataForManga(it.id!!).await()
                                    ?.raise<EHentaiSearchMetadata>()
                            }.toList()

                            fun metaInRelativeDuration(duration: Interval<*>): Int {
                                val durationMs = duration.inMilliseconds.longValue
                                return allMeta.asSequence().filter {
                                    System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                                }.count()
                            }

                            """
                            $statsText

                            Galleries that were checked in the last:
                            - hour: ${metaInRelativeDuration(1.hours)}
                            - 6 hours: ${metaInRelativeDuration(6.hours)}
                            - 12 hours: ${metaInRelativeDuration(12.hours)}
                            - day: ${metaInRelativeDuration(1.days)}
                            - 2 days: ${metaInRelativeDuration(2.days)}
                            - week: ${metaInRelativeDuration(7.days)}
                            - month: ${metaInRelativeDuration(30.days)}
                            - year: ${metaInRelativeDuration(365.days)}
                            """.trimIndent()
                        } finally {
                            progress.dismiss()
                        }

                        withContext(Dispatchers.Main) {
                            MaterialDialog(context)
                                .title(text = "Gallery updater statistics")
                                .message(text = updateInfo)
                                .positiveButton(android.R.string.ok)
                                .show()
                        }
                    }
                }
            }
        }
    }
}
