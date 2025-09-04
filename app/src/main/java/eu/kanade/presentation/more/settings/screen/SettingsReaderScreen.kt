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
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

@Suppress("unused")
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
                preference = readerPref.defaultReadingMode(),
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) }
                    .toImmutableMap(),
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.doubleTapAnimSpeed(),
                entries = persistentMapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showReadingMode(),
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showNavigationOverlayOnStart(),
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
            // KMK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.smallerTapZone(),
                title = stringResource(KMR.strings.pref_viewer_nav_smaller_tap_zone),
            ),
            // KMK <--
            // SY -->
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.forceHorizontalSeekbar(),
                title = stringResource(SYMR.strings.pref_force_horz_seekbar),
                subtitle = stringResource(SYMR.strings.pref_force_horz_seekbar_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.landscapeVerticalSeekbar(),
                title = stringResource(SYMR.strings.pref_show_vert_seekbar_landscape),
                subtitle = stringResource(SYMR.strings.pref_show_vert_seekbar_landscape_summary),
                enabled = !forceHorizontalSeekbar,
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.leftVerticalSeekbar(),
                title = stringResource(SYMR.strings.pref_left_handed_vertical_seekbar),
                subtitle = stringResource(SYMR.strings.pref_left_handed_vertical_seekbar_summary),
                enabled = !forceHorizontalSeekbar,
            ),
            // SY <--
            /* SY -->
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.pageTransitions(),
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
                    preference = readerPreferences.defaultOrientationType(),
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_rotation_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerTheme(),
                    entries = persistentMapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                    title = stringResource(MR.strings.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = fullscreenPref,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cutoutShort(),
                    title = stringResource(MR.strings.pref_cutout_short),
                    enabled = fullscreen &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        LocalView.current.rootWindowInsets?.displayCutout != null, // has cutout
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.keepScreenOn(),
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber(),
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
                    preference = readerPreferences.flashOnPageChange(),
                    title = stringResource(MR.strings.pref_flash_page),
                    subtitle = stringResource(MR.strings.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
                    valueRange = 1..15,
                    title = stringResource(MR.strings.pref_flash_duration),
                    subtitle = stringResource(MR.strings.pref_flash_duration_summary, flashMillis),
                    enabled = flashPageState,
                    onValueChanged = {
                        flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashInterval,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_flash_page_interval),
                    subtitle = pluralStringResource(MR.plurals.pref_pages, flashInterval, flashInterval),
                    enabled = flashPageState,
                    onValueChanged = {
                        flashIntervalPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = flashColorPref,
                    entries = persistentMapOf(
                        ReaderPreferences.FlashColor.BLACK to stringResource(MR.strings.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to stringResource(MR.strings.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(MR.strings.pref_flash_style_white_black),
                    ),
                    title = stringResource(MR.strings.pref_flash_with),
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
                    preference = readerPreferences.skipRead(),
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipFiltered(),
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipDupe(),
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysShowChapterTransition(),
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

        // KMK -->
        val pagedDisableZoomIn by readerPreferences.pagedDisableZoomIn().collectAsState()
        // KMK <--

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pagerNavInverted(),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = imageScaleTypePref,
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.zoomStart(),
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_zoom_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBorders(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pageTransitionsPager(),
                    title = stringResource(MR.strings.pref_page_transitions),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.landscapeZoom(),
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pagedDisableZoomIn(),
                    title = stringResource(KMR.strings.pref_paged_disable_zoom_in),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pagedDoubleTapZoomEnabled(),
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                    enabled = !pagedDisableZoomIn,
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.navigateToPan(),
                    title = stringResource(MR.strings.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvert(),
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
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonNavInverted(),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = webtoonSidePadding,
                    valueRange = ReaderPreferences.let {
                        it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX
                    },
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
                    subtitle = numberFormat.format(webtoonSidePadding / 100f),
                    onValueChanged = {
                        webtoonSidePaddingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerHideThreshold(),
                    entries = persistentMapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                    title = stringResource(MR.strings.pref_hide_threshold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersWebtoon(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertWebtoon(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvertWebtoon(),
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDoubleTapZoomEnabled(),
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonPinchToZoomEnabled(),
                    title = stringResource(KMR.strings.pref_pinch_to_zoom),
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDisableZoomOut(),
                    title = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pageTransitionsWebtoon(),
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
                    preference = readerPreferences.continuousVerticalTappingByPage(),
                    title = stringResource(SYMR.strings.tap_scroll_page),
                    subtitle = stringResource(SYMR.strings.tap_scroll_page_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersContinuousVertical(),
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
                    preference = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithVolumeKeysInverted(),
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
                    preference = readerPreferences.readWithLongTap(),
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.folderPerManga(),
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
                    preference = readerPreferences.preloadSize(),
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
                    title = stringResource(SYMR.strings.reader_preload_amount),
                    subtitle = stringResource(SYMR.strings.reader_preload_amount_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerThreads(),
                    title = stringResource(SYMR.strings.download_threads),
                    subtitle = stringResource(SYMR.strings.download_threads_summary),
                    entries = List(5) { it }.associateWith { it.toString() }.toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.cacheSize(),
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
                    preference = readerPreferences.aggressivePageLoading(),
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
                    preference = readerPreferences.readerInstantRetry(),
                    title = stringResource(SYMR.strings.skip_queue_on_retry),
                    subtitle = stringResource(SYMR.strings.skip_queue_on_retry_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.preserveReadingPosition(),
                    title = stringResource(SYMR.strings.preserve_reading_position),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.useAutoWebtoon(),
                    title = stringResource(SYMR.strings.auto_webtoon_mode),
                    subtitle = stringResource(SYMR.strings.auto_webtoon_mode_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = readerPreferences.readerBottomButtons(),
                    entries = ReaderBottomButton.entries
                        .associate { it.value to stringResource(it.stringRes) }
                        .toImmutableMap(),
                    title = stringResource(SYMR.strings.reader_bottom_buttons),
                    subtitle = stringResource(SYMR.strings.reader_bottom_buttons_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pageLayout(),
                    entries = ReaderPreferences.PageLayouts
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(SYMR.strings.page_layout),
                    subtitle = stringResource(SYMR.strings.automatic_can_still_switch),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.invertDoublePages(),
                    title = stringResource(SYMR.strings.invert_double_pages),
                    enabled = pageLayout != PagerConfig.PageLayout.SINGLE_PAGE,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.centerMarginType(),
                    entries = ReaderPreferences.CenterMarginTypes
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(SYMR.strings.center_margin),
                    subtitle = stringResource(SYMR.strings.pref_center_margin_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.archiveReaderMode(),
                    entries = ReaderPreferences.archiveModeTypes
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(SYMR.strings.pref_archive_reader_mode),
                    subtitle = stringResource(SYMR.strings.pref_archive_reader_mode_summary),
                ),
            ),
        )
    }
    // SY <--
}
