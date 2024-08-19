package eu.kanade.presentation.more.settings.screen

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsReaderScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerPref = remember { Injekt.get<ReaderPreferences>() }
        // SY -->
        val forceHorizontalSeekbar by readerPref.forceHorizontalSeekbar().collectAsState()
        // SY <--

        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.defaultReadingMode(),
                title = stringResource(MR.strings.pref_viewer_type),
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) }
                    .toImmutableMap(),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.doubleTapAnimSpeed(),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
                entries = persistentMapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showReadingMode(),
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showNavigationOverlayOnStart(),
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
            // SY -->
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.forceHorizontalSeekbar(),
                title = stringResource(SYMR.strings.pref_force_horz_seekbar),
                subtitle = stringResource(SYMR.strings.pref_force_horz_seekbar_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.landscapeVerticalSeekbar(),
                title = stringResource(SYMR.strings.pref_show_vert_seekbar_landscape),
                subtitle = stringResource(SYMR.strings.pref_show_vert_seekbar_landscape_summary),
                enabled = !forceHorizontalSeekbar,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.leftVerticalSeekbar(),
                title = stringResource(SYMR.strings.pref_left_handed_vertical_seekbar),
                subtitle = stringResource(SYMR.strings.pref_left_handed_vertical_seekbar_summary),
                enabled = !forceHorizontalSeekbar,
            ),
            // SY <--
            /* SY -->
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.pageTransitions(),
                title = stringResource(MR.strings.pref_page_transitions),
            ),
            SY <-- */
            getDisplayGroup(readerPreferences = readerPref),
            getEInkGroup(readerPreferences = readerPref),
            getReadingGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            // SY -->
            getContinuousVerticalGroup(readerPreferences = readerPref),
            // SY <--
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
            // SY -->
            getPageDownloadingGroup(readerPreferences = readerPref),
            getForkSettingsGroup(readerPreferences = readerPref),
            // SY <--
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreenPref = readerPreferences.fullscreen()
        val fullscreen by fullscreenPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.defaultOrientationType(),
                    title = stringResource(MR.strings.pref_rotation_type),
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerTheme(),
                    title = stringResource(MR.strings.pref_reader_theme),
                    entries = persistentMapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = fullscreenPref,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cutoutShort(),
                    title = stringResource(MR.strings.pref_cutout_short),
                    enabled = fullscreen &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        LocalView.current.rootWindowInsets?.displayCutout != null, // has cutout
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.keepScreenOn(),
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.showPageNumber(),
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getEInkGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val flashPageState by readerPreferences.flashOnPageChange().collectAsState()

        val flashMillisPref = readerPreferences.flashDurationMillis()
        val flashMillis by flashMillisPref.collectAsState()

        val flashIntervalPref = readerPreferences.flashPageInterval()
        val flashInterval by flashIntervalPref.collectAsState()

        val flashColorPref = readerPreferences.flashColor()

        return Preference.PreferenceGroup(
            title = "E-Ink",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.flashOnPageChange(),
                    title = stringResource(MR.strings.pref_flash_page),
                    subtitle = stringResource(MR.strings.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
                    min = 1,
                    max = 15,
                    title = stringResource(MR.strings.pref_flash_duration),
                    subtitle = stringResource(MR.strings.pref_flash_duration_summary, flashMillis),
                    onValueChanged = {
                        flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION)
                        true
                    },
                    enabled = flashPageState,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashInterval,
                    min = 1,
                    max = 10,
                    title = stringResource(MR.strings.pref_flash_page_interval),
                    subtitle = pluralStringResource(MR.plurals.pref_pages, flashInterval, flashInterval),
                    onValueChanged = {
                        flashIntervalPref.set(it)
                        true
                    },
                    enabled = flashPageState,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = flashColorPref,
                    title = stringResource(MR.strings.pref_flash_with),
                    entries = persistentMapOf(
                        ReaderPreferences.FlashColor.BLACK to stringResource(MR.strings.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to stringResource(MR.strings.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(MR.strings.pref_flash_style_white_black),
                    ),
                    enabled = flashPageState,
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reading),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipRead(),
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipFiltered(),
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipDupe(),
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.markReadDupe(),
                    title = stringResource(SYMR.strings.pref_mark_read_dupe_chapters),
                    subtitle = stringResource(SYMR.strings.pref_mark_read_dupe_chapters_summary),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.alwaysShowChapterTransition(),
                    title = stringResource(MR.strings.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager()
        val imageScaleTypePref = readerPreferences.imageScaleType()
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged()
        val rotateToFitPref = readerPreferences.dualPageRotateToFit()

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(MR.strings.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.pagerNavInverted(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = imageScaleTypePref,
                    title = stringResource(MR.strings.pref_image_scale_type),
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.zoomStart(),
                    title = stringResource(MR.strings.pref_zoom_start),
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBorders(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.pageTransitionsPager(),
                    title = stringResource(MR.strings.pref_page_transitions),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.landscapeZoom(),
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.navigateToPan(),
                    title = stringResource(MR.strings.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageRotateToFitInvert(),
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon()
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon()
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon()
        val webtoonSidePaddingPref = readerPreferences.webtoonSidePadding()

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()
        val webtoonSidePadding by webtoonSidePaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.webtoon_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(MR.strings.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.webtoonNavInverted(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = webtoonSidePadding,
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
                    subtitle = numberFormat.format(webtoonSidePadding / 100f),
                    min = ReaderPreferences.WEBTOON_PADDING_MIN,
                    max = ReaderPreferences.WEBTOON_PADDING_MAX,
                    onValueChanged = {
                        webtoonSidePaddingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerHideThreshold(),
                    title = stringResource(MR.strings.pref_hide_threshold),
                    entries = persistentMapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBordersWebtoon(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertWebtoon(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageRotateToFitInvertWebtoon(),
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.webtoonDoubleTapZoomEnabled(),
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.webtoonDisableZoomOut(),
                    title = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.pageTransitionsWebtoon(),
                    title = stringResource(MR.strings.pref_page_transitions),
                ),
                // SY <--
            ),
        )
    }

    // SY -->
    @Composable
    private fun getContinuousVerticalGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.vertical_plus_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.continuousVerticalTappingByPage(),
                    title = stringResource(SYMR.strings.tap_scroll_page),
                    subtitle = stringResource(SYMR.strings.tap_scroll_page_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBordersContinuousVertical(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
            ),
        )
    }
    // SY <--

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys()
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithVolumeKeysInverted(),
                    title = stringResource(MR.strings.pref_read_with_volume_keys_inverted),
                    enabled = readWithVolumeKeys,
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_actions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithLongTap(),
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.folderPerManga(),
                    title = stringResource(MR.strings.pref_create_folder_per_manga),
                    subtitle = stringResource(MR.strings.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }

    // SY -->
    @Composable
    private fun getPageDownloadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.page_downloading),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.preloadSize(),
                    title = stringResource(SYMR.strings.reader_preload_amount),
                    subtitle = stringResource(SYMR.strings.reader_preload_amount_summary),
                    entries = persistentMapOf(
                        4 to stringResource(SYMR.strings.reader_preload_amount_4_pages),
                        6 to stringResource(SYMR.strings.reader_preload_amount_6_pages),
                        8 to stringResource(SYMR.strings.reader_preload_amount_8_pages),
                        10 to stringResource(SYMR.strings.reader_preload_amount_10_pages),
                        12 to stringResource(SYMR.strings.reader_preload_amount_12_pages),
                        14 to stringResource(SYMR.strings.reader_preload_amount_14_pages),
                        16 to stringResource(SYMR.strings.reader_preload_amount_16_pages),
                        20 to stringResource(SYMR.strings.reader_preload_amount_20_pages),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerThreads(),
                    title = stringResource(SYMR.strings.download_threads),
                    subtitle = stringResource(SYMR.strings.download_threads_summary),
                    entries = List(5) { it }.associateWith { it.toString() }.toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.cacheSize(),
                    title = stringResource(SYMR.strings.reader_cache_size),
                    subtitle = stringResource(SYMR.strings.reader_cache_size_summary),
                    entries = persistentMapOf(
                        "50" to "50 MB",
                        "75" to "75 MB",
                        "100" to "100 MB",
                        "150" to "150 MB",
                        "250" to "250 MB",
                        "500" to "500 MB",
                        "750" to "750 MB",
                        "1000" to "1 GB",
                        "1500" to "1.5 GB",
                        "2000" to "2 GB",
                        "2500" to "2.5 GB",
                        "3000" to "3 GB",
                        "3500" to "3.5 GB",
                        "4000" to "4 GB",
                        "4500" to "4.5 GB",
                        "5000" to "5 GB",
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.aggressivePageLoading(),
                    title = stringResource(SYMR.strings.aggressively_load_pages),
                    subtitle = stringResource(SYMR.strings.aggressively_load_pages_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getForkSettingsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val pageLayout by readerPreferences.pageLayout().collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.pref_category_fork),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readerInstantRetry(),
                    title = stringResource(SYMR.strings.skip_queue_on_retry),
                    subtitle = stringResource(SYMR.strings.skip_queue_on_retry_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.preserveReadingPosition(),
                    title = stringResource(SYMR.strings.preserve_reading_position),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.useAutoWebtoon(),
                    title = stringResource(SYMR.strings.auto_webtoon_mode),
                    subtitle = stringResource(SYMR.strings.auto_webtoon_mode_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = readerPreferences.readerBottomButtons(),
                    title = stringResource(SYMR.strings.reader_bottom_buttons),
                    subtitle = stringResource(SYMR.strings.reader_bottom_buttons_summary),
                    entries = ReaderBottomButton.entries
                        .associate { it.value to stringResource(it.stringRes) }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.pageLayout(),
                    title = stringResource(SYMR.strings.page_layout),
                    subtitle = stringResource(SYMR.strings.automatic_can_still_switch),
                    entries = ReaderPreferences.PageLayouts
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.invertDoublePages(),
                    title = stringResource(SYMR.strings.invert_double_pages),
                    enabled = pageLayout != PagerConfig.PageLayout.SINGLE_PAGE,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.centerMarginType(),
                    title = stringResource(SYMR.strings.center_margin),
                    subtitle = stringResource(SYMR.strings.pref_center_margin_summary),
                    entries = ReaderPreferences.CenterMarginTypes
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.archiveReaderMode(),
                    title = stringResource(SYMR.strings.pref_archive_reader_mode),
                    subtitle = stringResource(SYMR.strings.pref_archive_reader_mode_summary),
                    entries = ReaderPreferences.archiveModeTypes
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
            ),
        )
    }
    // SY <--
}
