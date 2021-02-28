package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues.TappingInvertMode
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import kotlinx.coroutines.flow.launchIn
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsReaderController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_reader

        intListPreference {
            key = Keys.defaultViewer
            titleRes = R.string.pref_viewer_type
            entriesRes = arrayOf(
                R.string.left_to_right_viewer,
                R.string.right_to_left_viewer,
                R.string.vertical_viewer,
                R.string.webtoon_viewer,
                R.string.vertical_plus_viewer
            )
            entryValues = arrayOf("1", "2", "3", "4", "5")
            defaultValue = "2"
            summary = "%s"
        }
        intListPreference {
            key = Keys.doubleTapAnimationSpeed
            titleRes = R.string.pref_double_tap_anim_speed
            entries = arrayOf(context.getString(R.string.double_tap_anim_speed_0), context.getString(R.string.double_tap_anim_speed_normal), context.getString(R.string.double_tap_anim_speed_fast))
            entryValues = arrayOf("1", "500", "250") // using a value of 0 breaks the image viewer, so min is 1
            defaultValue = "500"
            summary = "%s"
        }
        switchPreference {
            key = Keys.showReadingMode
            titleRes = R.string.pref_show_reading_mode
            summaryRes = R.string.pref_show_reading_mode_summary
            defaultValue = true
        }
        switchPreference {
            key = Keys.landscapeVerticalSeekbar
            titleRes = R.string.pref_show_vert_seekbar_landscape
            summaryRes = R.string.pref_show_vert_seekbar_landscape_summary
            defaultValue = false
        }
        switchPreference {
            key = Keys.leftVerticalSeekbar
            titleRes = R.string.pref_left_handed_vertical_seekbar
            summaryRes = R.string.pref_left_handed_vertical_seekbar_summary
            defaultValue = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switchPreference {
                key = Keys.trueColor
                titleRes = R.string.pref_true_color
                summaryRes = R.string.pref_true_color_summary
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            intListPreference {
                key = Keys.rotation
                titleRes = R.string.pref_rotation_type
                entriesRes = arrayOf(
                    R.string.rotation_free,
                    R.string.rotation_lock,
                    R.string.rotation_force_portrait,
                    R.string.rotation_force_landscape
                )
                entryValues = arrayOf("1", "2", "3", "4")
                defaultValue = "1"
                summary = "%s"
            }
            intListPreference {
                key = Keys.readerTheme
                titleRes = R.string.pref_reader_theme
                entriesRes = arrayOf(R.string.black_background, R.string.gray_background, R.string.white_background, R.string.smart_based_on_page, R.string.smart_based_on_page_and_theme)
                entryValues = arrayOf("1", "2", "0", "3", "4")
                defaultValue = "3"
                summary = "%s"
            }
            switchPreference {
                key = Keys.fullscreen
                titleRes = R.string.pref_fullscreen
                defaultValue = true
            }

            if (activity?.hasDisplayCutout() == true) {
                switchPreference {
                    key = Keys.cutoutShort
                    titleRes = R.string.pref_cutout_short
                    defaultValue = true
                }
            }

            switchPreference {
                key = Keys.keepScreenOn
                titleRes = R.string.pref_keep_screen_on
                defaultValue = true
            }
            switchPreference {
                key = Keys.showPageNumber
                titleRes = R.string.pref_show_page_number
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_reading

            switchPreference {
                key = Keys.skipRead
                titleRes = R.string.pref_skip_read_chapters
                defaultValue = false
            }
            switchPreference {
                key = Keys.skipFiltered
                titleRes = R.string.pref_skip_filtered_chapters
                defaultValue = true
            }
            switchPreference {
                key = Keys.alwaysShowChapterTransition
                titleRes = R.string.pref_always_show_chapter_transition
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.pager_viewer

            intListPreference {
                key = Keys.navigationModePager
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.pager_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                defaultValue = "0"
                summary = "%s"

                preferences.readWithTapping().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
            listPreference {
                key = Keys.pagerNavInverted
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name
                )
                defaultValue = TappingInvertMode.NONE.name
                summary = "%s"

                preferences.readWithTapping().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
            intListPreference {
                key = Keys.imageScaleType
                titleRes = R.string.pref_image_scale_type
                entriesRes = arrayOf(
                    R.string.scale_type_fit_screen,
                    R.string.scale_type_stretch,
                    R.string.scale_type_fit_width,
                    R.string.scale_type_fit_height,
                    R.string.scale_type_original_size,
                    R.string.scale_type_smart_fit
                )
                entryValues = arrayOf("1", "2", "3", "4", "5", "6")
                defaultValue = "1"
                summary = "%s"
            }
            intListPreference {
                key = Keys.zoomStart
                titleRes = R.string.pref_zoom_start
                entriesRes = arrayOf(
                    R.string.zoom_start_automatic,
                    R.string.zoom_start_left,
                    R.string.zoom_start_right,
                    R.string.zoom_start_center
                )
                entryValues = arrayOf("1", "2", "3", "4")
                defaultValue = "1"
                summary = "%s"
            }
            switchPreference {
                key = Keys.cropBorders
                titleRes = R.string.pref_crop_borders
                defaultValue = false
            }
            // SY -->
            switchPreference {
                key = Keys.enableTransitionsPager
                titleRes = R.string.pref_page_transitions
                defaultValue = true
            }
            // SY <--
            switchPreference {
                key = Keys.dualPageSplitPaged
                titleRes = R.string.pref_dual_page_split
                defaultValue = false
            }
            switchPreference {
                key = Keys.dualPageInvertPaged
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                defaultValue = false
                preferences.dualPageSplitPaged().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
        }

        preferenceCategory {
            titleRes = R.string.webtoon_viewer

            intListPreference {
                key = Keys.navigationModeWebtoon
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.webtoon_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                defaultValue = "0"
                summary = "%s"

                preferences.readWithTapping().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
            listPreference {
                key = Keys.webtoonNavInverted
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name
                )
                defaultValue = TappingInvertMode.NONE.name
                summary = "%s"

                preferences.readWithTapping().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
            intListPreference {
                key = Keys.webtoonSidePadding
                titleRes = R.string.pref_webtoon_side_padding
                entriesRes = arrayOf(
                    R.string.webtoon_side_padding_0,
                    R.string.webtoon_side_padding_10,
                    R.string.webtoon_side_padding_15,
                    R.string.webtoon_side_padding_20,
                    R.string.webtoon_side_padding_25
                )
                entryValues = arrayOf("0", "10", "15", "20", "25")
                defaultValue = "0"
                summary = "%s"
            }
            switchPreference {
                key = Keys.cropBordersWebtoon
                titleRes = R.string.pref_crop_borders
                defaultValue = false
            }
            switchPreference {
                key = Keys.dualPageSplitWebtoon
                titleRes = R.string.pref_dual_page_split
                defaultValue = false
            }
            switchPreference {
                key = Keys.dualPageInvertWebtoon
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                defaultValue = false
                preferences.dualPageSplitWebtoon().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
            // SY -->
            switchPreference {
                key = Keys.enableTransitionsWebtoon
                titleRes = R.string.pref_page_transitions
                defaultValue = true
            }
            switchPreference {
                key = Keys.webtoonEnableZoomOut
                titleRes = R.string.enable_zoom_out
                defaultValue = false
            }
            // SY <--
        }

        // SY -->
        preferenceCategory {
            titleRes = R.string.vertical_plus_viewer

            switchPreference {
                key = Keys.continuousVerticalTappingByPage
                titleRes = R.string.tap_scroll_page
                summaryRes = R.string.tap_scroll_page_summary
                defaultValue = false
            }
            switchPreference {
                key = Keys.cropBordersContinuesVertical
                titleRes = R.string.pref_crop_borders
                defaultValue = false
            }
        }
        // SY <--

        preferenceCategory {
            titleRes = R.string.pref_reader_navigation

            switchPreference {
                key = Keys.readWithTapping
                titleRes = R.string.pref_read_with_tapping
                defaultValue = true
            }
            switchPreference {
                key = Keys.readWithLongTap
                titleRes = R.string.pref_read_with_long_tap
                defaultValue = true
            }
            switchPreference {
                key = Keys.readWithVolumeKeys
                titleRes = R.string.pref_read_with_volume_keys
                defaultValue = false
            }
            switchPreference {
                key = Keys.readWithVolumeKeysInverted
                titleRes = R.string.pref_read_with_volume_keys_inverted
                defaultValue = false

                preferences.readWithVolumeKeys().asImmediateFlow { isVisible = it }.launchIn(viewScope)
            }
        }

        // EXH -->
        preferenceCategory {
            titleRes = R.string.pref_category_fork

            intListPreference {
                key = Keys.eh_readerThreads
                titleRes = R.string.download_threads
                entries = arrayOf("1", "2", "3", "4", "5")
                entryValues = entries
                defaultValue = "2"
                summaryRes = R.string.download_threads_summary
            }
            switchPreference {
                key = Keys.eh_aggressivePageLoading
                titleRes = R.string.aggressively_load_pages
                summaryRes = R.string.aggressively_load_pages_summary
                defaultValue = false
            }
            switchPreference {
                key = Keys.eh_readerInstantRetry
                titleRes = R.string.skip_queue_on_retry
                summaryRes = R.string.skip_queue_on_retry_summary
                defaultValue = true
            }
            intListPreference {
                key = Keys.eh_preload_size
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
                    R.string.reader_preload_amount_20_pages
                )
                defaultValue = "10"
                summaryRes = R.string.reader_preload_amount_summary
            }
            listPreference {
                key = Keys.eh_cacheSize
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
                    "5000"
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
                    "5 GB"
                )
                defaultValue = "75"
                summaryRes = R.string.reader_cache_size_summary
            }
            switchPreference {
                key = Keys.eh_preserveReadingPosition
                titleRes = R.string.preserve_reading_position
                defaultValue = false
            }
            switchPreference {
                key = Keys.eh_use_auto_webtoon
                titleRes = R.string.auto_webtoon_mode
                summaryRes = R.string.auto_webtoon_mode_summary
                defaultValue = true
            }
        }
        // EXH <--
    }
}
