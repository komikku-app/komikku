package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferenceValues.TappingInvertMode
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SettingsReaderController : SettingsController() {

    private val readerPreferences: ReaderPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_reader

        intListPreference {
            bindTo(readerPreferences.defaultReadingMode())
            titleRes = R.string.pref_viewer_type
            entriesRes = arrayOf(
                R.string.left_to_right_viewer,
                R.string.right_to_left_viewer,
                R.string.vertical_viewer,
                R.string.webtoon_viewer,
                R.string.vertical_plus_viewer,
            )
            entryValues = ReadingModeType.values().drop(1)
                .map { value -> "${value.flagValue}" }.toTypedArray()
            summary = "%s"
        }
        intListPreference {
            bindTo(readerPreferences.doubleTapAnimSpeed())
            titleRes = R.string.pref_double_tap_anim_speed
            entries = arrayOf(context.getString(R.string.double_tap_anim_speed_0), context.getString(R.string.double_tap_anim_speed_normal), context.getString(R.string.double_tap_anim_speed_fast))
            entryValues = arrayOf("1", "500", "250") // using a value of 0 breaks the image viewer, so min is 1
            summary = "%s"
        }
        switchPreference {
            bindTo(readerPreferences.showReadingMode())
            titleRes = R.string.pref_show_reading_mode
            summaryRes = R.string.pref_show_reading_mode_summary
        }
        switchPreference {
            bindTo(readerPreferences.showNavigationOverlayOnStart())
            titleRes = R.string.pref_show_navigation_mode
            summaryRes = R.string.pref_show_navigation_mode_summary
        }
        // SY -->
        switchPreference {
            bindTo(readerPreferences.forceHorizontalSeekbar())
            titleRes = R.string.pref_force_horz_seekbar
            summaryRes = R.string.pref_force_horz_seekbar_summary
        }
        switchPreference {
            bindTo(readerPreferences.landscapeVerticalSeekbar())
            titleRes = R.string.pref_show_vert_seekbar_landscape
            summaryRes = R.string.pref_show_vert_seekbar_landscape_summary
            visibleIf(readerPreferences.forceHorizontalSeekbar()) { !it }
        }
        switchPreference {
            bindTo(readerPreferences.leftVerticalSeekbar())
            titleRes = R.string.pref_left_handed_vertical_seekbar
            summaryRes = R.string.pref_left_handed_vertical_seekbar_summary
            visibleIf(readerPreferences.forceHorizontalSeekbar()) { !it }
        }
        // SY <--
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switchPreference {
                bindTo(readerPreferences.trueColor())
                titleRes = R.string.pref_true_color
                summaryRes = R.string.pref_true_color_summary
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            intListPreference {
                bindTo(readerPreferences.defaultOrientationType())
                titleRes = R.string.pref_rotation_type
                entriesRes = arrayOf(
                    R.string.rotation_free,
                    R.string.rotation_portrait,
                    R.string.rotation_reverse_portrait,
                    R.string.rotation_landscape,
                    R.string.rotation_force_portrait,
                    R.string.rotation_force_landscape,
                )
                entryValues = OrientationType.values().drop(1)
                    .map { value -> "${value.flagValue}" }.toTypedArray()
                summary = "%s"
            }
            intListPreference {
                bindTo(readerPreferences.readerTheme())
                titleRes = R.string.pref_reader_theme
                entriesRes = arrayOf(R.string.black_background, R.string.gray_background, R.string.white_background, R.string.automatic_background)
                entryValues = arrayOf("1", "2", "0", "3")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.fullscreen())
                titleRes = R.string.pref_fullscreen
            }

            if (activity?.hasDisplayCutout() == true) {
                switchPreference {
                    bindTo(readerPreferences.cutoutShort())
                    titleRes = R.string.pref_cutout_short

                    visibleIf(readerPreferences.fullscreen()) { it }
                }
            }

            switchPreference {
                bindTo(readerPreferences.keepScreenOn())
                titleRes = R.string.pref_keep_screen_on
            }
            switchPreference {
                bindTo(readerPreferences.showPageNumber())
                titleRes = R.string.pref_show_page_number
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_reading

            switchPreference {
                bindTo(readerPreferences.skipRead())
                titleRes = R.string.pref_skip_read_chapters
            }
            switchPreference {
                bindTo(readerPreferences.skipFiltered())
                titleRes = R.string.pref_skip_filtered_chapters
            }
            switchPreference {
                bindTo(readerPreferences.alwaysShowChapterTransition())
                titleRes = R.string.pref_always_show_chapter_transition
            }
        }

        preferenceCategory {
            titleRes = R.string.pager_viewer

            intListPreference {
                bindTo(readerPreferences.navigationModePager())
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.pager_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.pagerNavInverted())
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both,
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name,
                )
                summary = "%s"
                visibleIf(readerPreferences.navigationModePager()) { it != 5 }
            }
            switchPreference {
                bindTo(readerPreferences.navigateToPan())
                titleRes = R.string.pref_navigate_pan
                visibleIf(readerPreferences.navigationModePager()) { it != 5 }
            }
            intListPreference {
                bindTo(readerPreferences.imageScaleType())
                titleRes = R.string.pref_image_scale_type
                entriesRes = arrayOf(
                    R.string.scale_type_fit_screen,
                    R.string.scale_type_stretch,
                    R.string.scale_type_fit_width,
                    R.string.scale_type_fit_height,
                    R.string.scale_type_original_size,
                    R.string.scale_type_smart_fit,
                )
                entryValues = arrayOf("1", "2", "3", "4", "5", "6")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.landscapeZoom())
                titleRes = R.string.pref_landscape_zoom
                visibleIf(readerPreferences.imageScaleType()) { it == 1 }
            }
            intListPreference {
                bindTo(readerPreferences.zoomStart())
                titleRes = R.string.pref_zoom_start
                entriesRes = arrayOf(
                    R.string.zoom_start_automatic,
                    R.string.zoom_start_left,
                    R.string.zoom_start_right,
                    R.string.zoom_start_center,
                )
                entryValues = arrayOf("1", "2", "3", "4")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.cropBorders())
                titleRes = R.string.pref_crop_borders
            }
            // SY -->
            switchPreference {
                bindTo(readerPreferences.pageTransitionsPager())
                titleRes = R.string.pref_page_transitions
            }
            // SY <--
            switchPreference {
                bindTo(readerPreferences.dualPageSplitPaged())
                titleRes = R.string.pref_dual_page_split
            }
            switchPreference {
                bindTo(readerPreferences.dualPageInvertPaged())
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                visibleIf(readerPreferences.dualPageSplitPaged()) { it }
            }
        }

        preferenceCategory {
            titleRes = R.string.webtoon_viewer

            intListPreference {
                bindTo(readerPreferences.navigationModeWebtoon())
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.webtoon_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.webtoonNavInverted())
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both,
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name,
                )
                summary = "%s"
                visibleIf(readerPreferences.navigationModeWebtoon()) { it != 5 }
            }
            intListPreference {
                bindTo(readerPreferences.webtoonSidePadding())
                titleRes = R.string.pref_webtoon_side_padding
                entriesRes = arrayOf(
                    R.string.webtoon_side_padding_0,
                    R.string.webtoon_side_padding_5,
                    R.string.webtoon_side_padding_10,
                    R.string.webtoon_side_padding_15,
                    R.string.webtoon_side_padding_20,
                    R.string.webtoon_side_padding_25,
                )
                entryValues = arrayOf("0", "5", "10", "15", "20", "25")
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.readerHideThreshold())
                titleRes = R.string.pref_hide_threshold
                entriesRes = arrayOf(
                    R.string.pref_highest,
                    R.string.pref_high,
                    R.string.pref_low,
                    R.string.pref_lowest,
                )
                entryValues = PreferenceValues.ReaderHideThreshold.values()
                    .map { it.name }
                    .toTypedArray()
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.cropBordersWebtoon())
                titleRes = R.string.pref_crop_borders
            }
            switchPreference {
                bindTo(readerPreferences.dualPageSplitWebtoon())
                titleRes = R.string.pref_dual_page_split
            }
            switchPreference {
                bindTo(readerPreferences.dualPageInvertWebtoon())
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                visibleIf(readerPreferences.dualPageSplitWebtoon()) { it }
            }
            switchPreference {
                bindTo(readerPreferences.longStripSplitWebtoon())
                titleRes = R.string.pref_long_strip_split
                summaryRes = R.string.split_tall_images_summary
            }
            // SY -->
            switchPreference {
                bindTo(readerPreferences.pageTransitionsWebtoon())
                titleRes = R.string.pref_page_transitions
            }
            switchPreference {
                bindTo(readerPreferences.webtoonEnableZoomOut())
                titleRes = R.string.enable_zoom_out
            }
            // SY <--
        }

        // SY -->
        preferenceCategory {
            titleRes = R.string.vertical_plus_viewer

            switchPreference {
                bindTo(readerPreferences.continuousVerticalTappingByPage())
                titleRes = R.string.tap_scroll_page
                summaryRes = R.string.tap_scroll_page_summary
            }
            switchPreference {
                bindTo(readerPreferences.cropBordersContinuousVertical())
                titleRes = R.string.pref_crop_borders
            }
        }
        // SY <--

        preferenceCategory {
            titleRes = R.string.pref_reader_navigation

            switchPreference {
                bindTo(readerPreferences.readWithVolumeKeys())
                titleRes = R.string.pref_read_with_volume_keys
            }
            switchPreference {
                bindTo(readerPreferences.readWithVolumeKeysInverted())
                titleRes = R.string.pref_read_with_volume_keys_inverted
                visibleIf(readerPreferences.readWithVolumeKeys()) { it }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_reader_actions

            switchPreference {
                bindTo(readerPreferences.readWithLongTap())
                titleRes = R.string.pref_read_with_long_tap
            }
            switchPreference {
                bindTo(readerPreferences.folderPerManga())
                titleRes = R.string.pref_create_folder_per_manga
                summaryRes = R.string.pref_create_folder_per_manga_summary
            }
        }

        // SY -->
        preferenceCategory {
            titleRes = R.string.page_downloading

            intListPreference {
                bindTo(readerPreferences.preloadSize())
                titleRes = R.string.reader_preload_amount
                entryValues = arrayOf(
                    "4",
                    "6",
                    "8",
                    "10",
                    "12",
                    "14",
                    "16",
                    "20",
                )
                entriesRes = arrayOf(
                    R.string.reader_preload_amount_4_pages,
                    R.string.reader_preload_amount_6_pages,
                    R.string.reader_preload_amount_8_pages,
                    R.string.reader_preload_amount_10_pages,
                    R.string.reader_preload_amount_12_pages,
                    R.string.reader_preload_amount_14_pages,
                    R.string.reader_preload_amount_16_pages,
                    R.string.reader_preload_amount_20_pages,
                )
                summaryRes = R.string.reader_preload_amount_summary
            }

            intListPreference {
                bindTo(readerPreferences.readerThreads())
                titleRes = R.string.download_threads
                entries = arrayOf("1", "2", "3", "4", "5")
                entryValues = entries
                summaryRes = R.string.download_threads_summary
            }

            listPreference {
                bindTo(readerPreferences.cacheSize())
                titleRes = R.string.reader_cache_size
                entryValues = arrayOf(
                    "50",
                    "75",
                    "100",
                    "150",
                    "250",
                    "500",
                    "750",
                    "1000",
                    "1500",
                    "2000",
                    "2500",
                    "3000",
                    "3500",
                    "4000",
                    "4500",
                    "5000",
                )
                entries = arrayOf(
                    "50 MB",
                    "75 MB",
                    "100 MB",
                    "150 MB",
                    "250 MB",
                    "500 MB",
                    "750 MB",
                    "1 GB",
                    "1.5 GB",
                    "2 GB",
                    "2.5 GB",
                    "3 GB",
                    "3.5 GB",
                    "4 GB",
                    "4.5 GB",
                    "5 GB",
                )
                summaryRes = R.string.reader_cache_size_summary
            }
            switchPreference {
                bindTo(readerPreferences.aggressivePageLoading())
                titleRes = R.string.aggressively_load_pages
                summaryRes = R.string.aggressively_load_pages_summary
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_fork

            switchPreference {
                bindTo(readerPreferences.readerInstantRetry())
                titleRes = R.string.skip_queue_on_retry
                summaryRes = R.string.skip_queue_on_retry_summary
            }

            switchPreference {
                bindTo(readerPreferences.preserveReadingPosition())
                titleRes = R.string.preserve_reading_position
            }
            switchPreference {
                bindTo(readerPreferences.useAutoWebtoon())
                titleRes = R.string.auto_webtoon_mode
                summaryRes = R.string.auto_webtoon_mode_summary
            }

            preference {
                key = "reader_bottom_buttons_pref"
                titleRes = R.string.reader_bottom_buttons
                summaryRes = R.string.reader_bottom_buttons_summary

                onClick {
                    ReaderBottomButtonsDialog().showDialog(router)
                }
            }
            intListPreference {
                bindTo(readerPreferences.pageLayout())
                titleRes = R.string.page_layout
                summaryRes = R.string.automatic_can_still_switch
                entriesRes = arrayOf(
                    R.string.single_page,
                    R.string.double_pages,
                    R.string.automatic_orientation,
                )
                entryValues = arrayOf("0", "1", "2")
            }
            switchPreference {
                bindTo(readerPreferences.invertDoublePages())
                titleRes = R.string.invert_double_pages
                visibleIf(readerPreferences.pageLayout()) { it != PagerConfig.PageLayout.SINGLE_PAGE }
            }

            intListPreference {
                bindTo(readerPreferences.centerMarginType())
                titleRes = R.string.center_margin
                entriesRes = arrayOf(
                    R.string.center_margin_none,
                    R.string.center_margin_double_page,
                    R.string.center_margin_wide_page,
                    R.string.center_margin_double_and_wide_page,
                )
                entryValues = arrayOf("0", "1", "2", "3")
            }
        }
        // SY <--
    }

    // SY -->
    class ReaderBottomButtonsDialog : DialogController() {

        private val readerPreferences: ReaderPreferences = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val oldSelection = readerPreferences.readerBottomButtons().get()
            val values = ReaderBottomButton.values()

            val selection = values.map { it.value in oldSelection }
                .toBooleanArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.reader_bottom_buttons)
                .setMultiChoiceItems(
                    values.map { activity!!.getString(it.stringRes) }.toTypedArray(),
                    selection,
                ) { _, which, selected ->
                    selection[which] = selected
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = values
                        .filterIndexed { index, _ ->
                            selection[index]
                        }
                        .map { it.value }
                        .toSet()

                    readerPreferences.readerBottomButtons().set(included)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
    // SY <--
}
