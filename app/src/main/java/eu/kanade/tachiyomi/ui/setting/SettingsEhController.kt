package eu.kanade.tachiyomi.ui.setting

import android.content.Context
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
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.databinding.EhDialogCategoriesBinding
import eu.kanade.tachiyomi.databinding.EhDialogLanguagesBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
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
import exh.eh.EHentaiUpdateWorker
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.EHentaiUpdaterStats
import exh.favorites.FavoritesIntroDialog
import exh.favorites.LocalFavoritesStorage
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.isEhBasedManga
import exh.uconfig.WarnConfigureDialogController
import exh.ui.login.LoginController
import exh.util.executeOnIO
import exh.util.floor
import exh.util.nullIfBlank
import exh.util.trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.hours
import kotlin.time.milliseconds
import kotlin.time.minutes
import kotlin.time.seconds

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
            .launchIn(viewScope)

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
                    .launchIn(viewScope)

                onChange { newVal ->
                    newVal as Boolean
                    if (!newVal) {
                        preferences.enableExhentai().set(false)
                        true
                    } else {
                        router.pushController(LoginController().withFadeTransaction())
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
                    .launchIn(viewScope)
            }

            switchPreference {
                titleRes = R.string.show_japanese_titles
                summaryOn = context.getString(R.string.show_japanese_titles_option_1)
                summaryOff = context.getString(R.string.show_japanese_titles_option_2)
                key = "use_jp_title"
                defaultValue = false

                onChange { preferences.useJapaneseTitle().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                titleRes = R.string.use_original_images
                summaryOn = context.getString(R.string.use_original_images_on)
                summaryOff = context.getString(R.string.use_original_images_off)
                key = PreferenceKeys.eh_useOrigImages
                defaultValue = false

                onChange { preferences.exhUseOriginalImages().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
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
                    .launchIn(viewScope)
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
                    .launchIn(viewScope)
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
                    .launchIn(viewScope)
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
                            val binding = EhDialogLanguagesBinding.bind(customView)

                            val languages = with(binding) {
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
                                    "${otherOriginal.isChecked}*${otherTranslated.isChecked}*${otherRewrite.isChecked}"
                                ).joinToString("\n")
                            }

                            preferences.exhSettingsLanguages().set(languages)

                            preferences.exhSettingsLanguages().reconfigure()
                        }
                        .show {
                            val customView = this.view.contentLayout.customView!!
                            val binding = EhDialogLanguagesBinding.bind(customView)
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

                            with(binding) {
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
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
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
                        .positiveButton { dialog ->
                            val customView = dialog.view.contentLayout.customView!!
                            val binding = EhDialogCategoriesBinding.bind(customView)

                            with(binding) {
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
                                        (!miscCheckbox.isChecked)
                                    ).joinToString(separator = ",") { it.toString() }
                                )
                            }

                            preferences.exhEnabledCategories().reconfigure()
                        }
                        .show {
                            val customView = this.view.contentLayout.customView!!
                            val binding = EhDialogCategoriesBinding.bind(customView)

                            with(binding) {
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
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                defaultValue = false
                key = PreferenceKeys.eh_watched_list_default_state
                titleRes = R.string.watched_list_default
                summaryRes = R.string.watched_list_state_summary

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
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
                    .launchIn(viewScope)
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

                preferences.exhAutoUpdateFrequency().asFlow()
                    .onEach { newVal ->
                        summary = if (newVal == 0) {
                            context.getString(R.string.time_between_batches_summary_1, context.getString(R.string.app_name))
                        } else {
                            context.getString(R.string.time_between_batches_summary_2, context.getString(R.string.app_name), newVal, EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION)
                        }
                    }
                    .launchIn(viewScope)

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    EHentaiUpdateWorker.scheduleBackground(context, interval)
                    true
                }
            }

            multiSelectListPreference {
                key = PreferenceKeys.eh_autoUpdateRestrictions
                titleRes = R.string.auto_update_restrictions
                entriesRes = arrayOf(R.string.network_unmetered, R.string.charging)
                entryValues = arrayOf("wifi", "ac")
                summaryRes = R.string.pref_library_update_restriction_summary

                preferences.exhAutoUpdateFrequency().asFlow()
                    .onEach { isVisible = it > 0 }
                    .launchIn(viewScope)

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

                    @OptIn(ExperimentalTime::class)
                    viewScope.launch(Dispatchers.IO) {
                        val updateInfo = try {
                            val stats =
                                preferences.exhAutoUpdateStats().get().nullIfBlank()?.let {
                                    Json.decodeFromString<EHentaiUpdaterStats>(it)
                                }

                            val statsText = if (stats != null) {
                                context.getString(R.string.gallery_updater_stats_text, getRelativeTimeString(getRelativeTimeFromNow(stats.startTime.milliseconds), context), stats.updateCount, stats.possibleUpdates)
                            } else context.getString(R.string.gallery_updater_not_ran_yet)

                            val allMeta = db.getFavoriteMangaWithMetadata().executeOnIO()
                                .filter(Manga::isEhBasedManga)
                                .mapNotNull {
                                    db.getFlatMetadataForManga(it.id!!).executeOnIO()
                                        ?.raise<EHentaiSearchMetadata>()
                                }.toList()

                            fun metaInRelativeDuration(duration: Duration): Int {
                                val durationMs = duration.toLongMilliseconds()
                                return allMeta.asSequence().filter {
                                    System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                                }.count()
                            }

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

    @OptIn(ExperimentalTime::class)
    private fun getRelativeTimeFromNow(then: Duration): RelativeTime {
        val now = System.currentTimeMillis().milliseconds
        var period: Duration = now - then
        val relativeTime = RelativeTime()
        while (period > 0.milliseconds) {
            when {
                period >= 365.days -> {
                    (period.inDays / 365).floor().let {
                        relativeTime.years = it
                        period -= (it * 365).days
                    }
                    continue
                }
                period >= 30.days -> {
                    (period.inDays / 30).floor().let {
                        relativeTime.months = it
                        period -= (it * 30).days
                    }
                }
                period >= 7.days -> {
                    (period.inDays / 7).floor().let {
                        relativeTime.weeks = it
                        period -= (it * 7).days
                    }
                }
                period >= 1.days -> {
                    period.inDays.floor().let {
                        relativeTime.days = it
                        period -= it.days
                    }
                }
                period >= 1.hours -> {
                    period.inHours.floor().let {
                        relativeTime.hours = it
                        period -= it.hours
                    }
                }
                period >= 1.minutes -> {
                    period.inMinutes.floor().let {
                        relativeTime.minutes = it
                        period -= it.minutes
                    }
                }
                period >= 1.seconds -> {
                    period.inSeconds.floor().let {
                        relativeTime.seconds = it
                        period -= it.seconds
                    }
                }
                period >= 1.milliseconds -> {
                    period.inMilliseconds.floor().let {
                        relativeTime.milliseconds = it
                    }
                    period = 0.milliseconds
                }
            }
        }
        return relativeTime
    }

    private fun getRelativeTimeString(relativeTime: RelativeTime, context: Context): String {
        return relativeTime.years?.let { context.resources.getQuantityString(R.plurals.humanize_year, it, it) }
            ?: relativeTime.months?.let { context.resources.getQuantityString(R.plurals.humanize_month, it, it) }
            ?: relativeTime.weeks?.let { context.resources.getQuantityString(R.plurals.humanize_week, it, it) }
            ?: relativeTime.days?.let { context.resources.getQuantityString(R.plurals.humanize_day, it, it) }
            ?: relativeTime.hours?.let { context.resources.getQuantityString(R.plurals.humanize_hour, it, it) }
            ?: relativeTime.minutes?.let { context.resources.getQuantityString(R.plurals.humanize_minute, it, it) }
            ?: relativeTime.seconds?.let { context.resources.getQuantityString(R.plurals.humanize_second, it, it) }
            ?: context.getString(R.string.humanize_fallback)
    }

    data class RelativeTime(var years: Int? = null, var months: Int? = null, var weeks: Int? = null, var days: Int? = null, var hours: Int? = null, var minutes: Int? = null, var seconds: Int? = null, var milliseconds: Int? = null)
}
