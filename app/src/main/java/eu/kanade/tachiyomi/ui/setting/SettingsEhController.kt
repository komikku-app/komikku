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
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
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
import eu.kanade.tachiyomi.util.preference.titleRes
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
import exh.uconfig.WarnConfigureDialogController
import exh.ui.login.LoginController
import exh.util.await
import exh.util.nullIfBlank
import exh.util.trans
import humanize.Humanize
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.util.Date
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.hours

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
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

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_eh

        preferenceCategory {
            titleRes = R.string.ehentai_prefs_account_settings

            switchPreference {
                titleRes = R.string.enable_exhentai
                summaryOff = context.getString(R.string.requires_login)
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
                titleRes = R.string.use_hentai_at_home

                key = PreferenceKeys.eh_enable_hah
                summaryRes = R.string.use_hentai_at_home_summary
                entriesRes = arrayOf(
                    R.string.use_hentai_at_home_option_1,
                    R.string.use_hentai_at_home_option_2
                )
                entryValues = arrayOf("0", "1")

                onChange { preferences.useHentaiAtHome().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            switchPreference {
                titleRes = R.string.show_japanese_titles
                summaryOn = context.getString(R.string.show_japanese_titles_option_1)
                summaryOff = context.getString(R.string.show_japanese_titles_option_2)
                key = "use_jp_title"
                defaultValue = false

                onChange { preferences.useJapaneseTitle().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            switchPreference {
                titleRes = R.string.use_original_images
                summaryOn = context.getString(R.string.use_original_images_on)
                summaryOff = context.getString(R.string.use_original_images_off)
                key = PreferenceKeys.eh_useOrigImages
                defaultValue = false

                onChange { preferences.eh_useOriginalImages().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            preference {
                key = "pref_watched_tags"
                titleRes = R.string.watched_tags
                summaryRes = R.string.watched_tags_summary
                onClick {
                    val intent = if (preferences.enableExhentai().get()) {
                        WebViewActivity.newIntent(activity!!, url = "https://exhentai.org/mytags", title = context.getString(R.string.watched_tags_exh))
                    } else {
                        WebViewActivity.newIntent(activity!!, url = "https://e-hentai.org/mytags", title = context.getString(R.string.watched_tags_eh))
                    }
                    startActivity(intent)
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            preference {
                titleRes = R.string.tag_filtering_threshold
                key = PreferenceKeys.eh_tag_filtering_value
                defaultValue = 0

                summary = context.getString(R.string.tag_filtering_threshhold_summary, preferences.ehTagFilterValue().get())

                onClick {
                    MaterialDialog(activity!!)
                        .title(R.string.tag_filtering_threshold)
                        .input(
                            inputType = InputType.TYPE_NUMBER_FLAG_SIGNED,
                            waitForPositiveButton = false,
                            allowEmpty = false
                        ) { dialog, number ->
                            val inputField = dialog.getInputField()
                            val value = number.toString().toIntOrNull()

                            if ((value != null && value in -9999..0) || number.toString() == "-") {
                                inputField.error = null
                            } else {
                                inputField.error = context.getString(R.string.tag_filtering_threshhold_error)
                            }
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, value != null && value in -9999..0)
                        }
                        .positiveButton(android.R.string.ok) {
                            val value = it.getInputField().text.toString().toInt()
                            preferences.ehTagFilterValue().set(value)
                            summary = context.getString(R.string.tag_filtering_threshhold_summary, preferences.ehTagFilterValue().get())
                            preferences.ehTagFilterValue().reconfigure()
                        }
                        .negativeButton(android.R.string.cancel)
                        .show()
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            preference {
                titleRes = R.string.tag_watching_threshhold
                key = PreferenceKeys.eh_tag_watching_value
                defaultValue = 0

                summary = context.getString(R.string.tag_watching_threshhold_summary, preferences.ehTagWatchingValue().get())

                onClick {
                    MaterialDialog(activity!!)
                        .title(R.string.tag_watching_threshhold)
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
                                inputField.error = context.getString(R.string.tag_watching_threshhold_error)
                            }
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, value != null && value in 0..9999)
                        }
                        .positiveButton(android.R.string.ok) {
                            val value = it.getInputField().text.toString().toInt()
                            preferences.ehTagWatchingValue().set(value)
                            summary = context.getString(R.string.tag_watching_threshhold_summary, preferences.ehTagWatchingValue().get())
                            preferences.ehTagWatchingValue().reconfigure()
                        }
                        .negativeButton(android.R.string.cancel)
                        .show()
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            preference {
                key = "pref_language_filtering"
                titleRes = R.string.language_filtering
                summaryRes = R.string.language_filtering_summary

                onClick {
                    MaterialDialog(activity!!)
                        .title(R.string.language_filtering)
                        .message(R.string.language_filtering_summary)
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

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            preference {
                key = "pref_front_page_categories"
                titleRes = R.string.frong_page_categories
                summaryRes = R.string.fromt_page_categories_summary

                onClick {
                    MaterialDialog(activity!!)
                        .title(R.string.frong_page_categories)
                        .message(R.string.fromt_page_categories_summary)
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

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            switchPreference {
                defaultValue = false
                key = PreferenceKeys.eh_watched_list_default_state
                titleRes = R.string.watched_list_default
                summaryRes = R.string.watched_list_state_summary

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            listPreference {
                defaultValue = "auto"
                key = PreferenceKeys.eh_ehentai_quality
                summaryRes = R.string.eh_image_quality_summary
                titleRes = R.string.eh_image_quality
                entriesRes = arrayOf(
                    R.string.eh_image_quality_auto,
                    R.string.eh_image_quality_2400,
                    R.string.eh_image_quality_1600,
                    R.string.eh_image_quality_1280,
                    R.string.eh_image_quality_980,
                    R.string.eh_image_quality_780
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

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(scope)
            }

            switchPreference {
                titleRes = R.string.pref_enhanced_e_hentai_view
                summaryRes = R.string.pref_enhanced_e_hentai_view_summary
                key = PreferenceKeys.enhancedEHentaiView
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.favorites_sync

            switchPreference {
                titleRes = R.string.disable_favorites_uploading
                summaryRes = R.string.disable_favorites_uploading_summary
                key = PreferenceKeys.eh_readOnlySync
                defaultValue = false
            }

            preference {
                key = "pref_show_sync_favorite_notes"
                titleRes = R.string.show_favorite_sync_notes
                summaryRes = R.string.show_favorite_sync_notes_summary

                onClick {
                    activity?.let {
                        FavoritesIntroDialog().show(it)
                    }
                }
            }

            switchPreference {
                titleRes = R.string.ignore_sync_errors
                summaryRes = R.string.ignore_sync_errors_summary
                key = PreferenceKeys.eh_lenientSync
                defaultValue = false
            }

            preference {
                key = "pref_force_sync_reset"
                titleRes = R.string.force_sync_state_reset
                summaryRes = R.string.force_sync_state_reset_summary

                onClick {
                    activity?.let { activity ->
                        MaterialDialog(activity)
                            .title(R.string.favorites_sync_reset)
                            .message(R.string.favorites_sync_reset_message)
                            .positiveButton(android.R.string.yes) {
                                LocalFavoritesStorage().apply {
                                    getRealm().use {
                                        it.trans {
                                            clearSnapshots(it)
                                        }
                                    }
                                }
                                activity.toast(context.getString(R.string.sync_state_reset), Toast.LENGTH_LONG)
                            }
                            .negativeButton(android.R.string.no)
                            .cancelable(false)
                            .show()
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.gallery_update_checker

            intListPreference {
                key = PreferenceKeys.eh_autoUpdateFrequency
                titleRes = R.string.time_between_batches
                entriesRes = arrayOf(
                    R.string.time_between_batches_never,
                    R.string.time_between_batches_1_hour,
                    R.string.time_between_batches_2_hours,
                    R.string.time_between_batches_3_hours,
                    R.string.time_between_batches_6_hours,
                    R.string.time_between_batches_12_hours,
                    R.string.time_between_batches_24_hours,
                    R.string.time_between_batches_48_hours
                )
                entryValues = arrayOf("0", "1", "2", "3", "6", "12", "24", "48")
                defaultValue = "0"

                preferences.eh_autoUpdateFrequency().asFlow()
                    .onEach { newVal ->
                        summary = if (newVal == 0) {
                            context.getString(R.string.time_between_batches_summary_1, context.getString(R.string.app_name))
                        } else {
                            context.getString(R.string.time_between_batches_summary_2, context.getString(R.string.app_name), newVal, EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION)
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
                titleRes = R.string.auto_update_restrictions
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
                key = "pref_show_updater_statistics"
                titleRes = R.string.show_updater_statistics

                onClick {
                    val progress = MaterialDialog(context)
                        .message(R.string.gallery_updater_statistics_collection)
                        .cancelable(false)
                    progress.show()

                    GlobalScope.launch(Dispatchers.IO) {
                        val updateInfo = try {
                            val stats =
                                preferences.eh_autoUpdateStats().get().nullIfBlank()?.let {
                                    Json.decodeFromString<EHentaiUpdaterStats>(it)
                                }

                            val statsText = if (stats != null) {
                                context.getString(R.string.gallery_updater_stats_text, Humanize.naturalTime(Date(stats.startTime)), stats.updateCount, stats.possibleUpdates)
                            } else context.getString(R.string.gallery_updater_not_ran_yet)

                            val allMeta = db.getFavoriteMangaWithMetadata().await().filter {
                                it.source == EH_SOURCE_ID || it.source == EXH_SOURCE_ID
                            }.mapNotNull {
                                db.getFlatMetadataForManga(it.id!!).await()
                                    ?.raise<EHentaiSearchMetadata>()
                            }.toList()

                            @OptIn(ExperimentalTime::class)
                            fun metaInRelativeDuration(duration: Duration): Int {
                                val durationMs = duration.toLongMilliseconds()
                                return allMeta.asSequence().filter {
                                    System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                                }.count()
                            }

                            @OptIn(ExperimentalTime::class)
                            statsText + "\n\n" + context.getString(
                                R.string.gallery_updater_stats_time,
                                metaInRelativeDuration(1.hours),
                                metaInRelativeDuration(6.hours),
                                metaInRelativeDuration(12.hours),
                                metaInRelativeDuration(1.days),
                                metaInRelativeDuration(2.days),
                                metaInRelativeDuration(7.days),
                                metaInRelativeDuration(30.days),
                                metaInRelativeDuration(365.days)
                            )
                        } finally {
                            progress.dismiss()
                        }

                        withContext(Dispatchers.Main) {
                            MaterialDialog(context)
                                .title(R.string.gallery_updater_statistics)
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
