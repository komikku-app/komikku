package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.domain.manga.model.Manga as DomainManga
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class PreferencesHelper(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads",
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup",
    ).toUri()

    fun confirmExit() = this.preferenceStore.getBoolean(Keys.confirmExit, false)

    fun sideNavIconAlignment() = this.preferenceStore.getInt("pref_side_nav_icon_alignment", 0)

    fun autoUpdateMetadata() = this.preferenceStore.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = this.preferenceStore.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = this.preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light },
    )

    fun appTheme() = this.preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = this.preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    // SY -->
    fun pageTransitionsPager() = this.preferenceStore.getBoolean("pref_enable_transitions_pager_key", true)

    fun pageTransitionsWebtoon() = this.preferenceStore.getBoolean("pref_enable_transitions_webtoon_key", true)
    // SY <--

    fun doubleTapAnimSpeed() = this.preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = this.preferenceStore.getBoolean("pref_show_page_number_key", true)

    fun dualPageSplitPaged() = this.preferenceStore.getBoolean("pref_dual_page_split", false)

    fun dualPageInvertPaged() = this.preferenceStore.getBoolean("pref_dual_page_invert", false)

    fun dualPageSplitWebtoon() = this.preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertWebtoon() = this.preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    fun longStripSplitWebtoon() = this.preferenceStore.getBoolean("pref_long_strip_split_webtoon", true)

    fun showReadingMode() = this.preferenceStore.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = this.preferenceStore.getBoolean("pref_true_color_key", false)

    fun fullscreen() = this.preferenceStore.getBoolean("fullscreen", true)

    fun cutoutShort() = this.preferenceStore.getBoolean("cutout_short", true)

    fun keepScreenOn() = this.preferenceStore.getBoolean("pref_keep_screen_on_key", true)

    fun customBrightness() = this.preferenceStore.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = this.preferenceStore.getInt("custom_brightness_value", 0)

    fun colorFilter() = this.preferenceStore.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = this.preferenceStore.getInt("color_filter_value", 0)

    fun colorFilterMode() = this.preferenceStore.getInt("color_filter_mode", 0)

    fun grayscale() = this.preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = this.preferenceStore.getBoolean("pref_inverted_colors", false)

    fun defaultReadingMode() = this.preferenceStore.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = this.preferenceStore.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = this.preferenceStore.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = this.preferenceStore.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = this.preferenceStore.getInt("pref_reader_theme_key", 3)

    fun alwaysShowChapterTransition() = this.preferenceStore.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = this.preferenceStore.getBoolean("crop_borders", false)

    fun navigateToPan() = this.preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = this.preferenceStore.getBoolean("landscape_zoom", true)

    fun cropBordersWebtoon() = this.preferenceStore.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = this.preferenceStore.getInt("webtoon_side_padding", 0)

    fun readWithTapping() = this.preferenceStore.getBoolean("reader_tap", true)

    fun pagerNavInverted() = this.preferenceStore.getEnum("reader_tapping_inverted", Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = this.preferenceStore.getEnum("reader_tapping_inverted_webtoon", Values.TappingInvertMode.NONE)

    fun readWithLongTap() = this.preferenceStore.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = this.preferenceStore.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = this.preferenceStore.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = this.preferenceStore.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = this.preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    fun showNavigationOverlayNewUser() = this.preferenceStore.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = this.preferenceStore.getBoolean("reader_navigation_overlay_on_start", false)

    fun readerHideThreshold() = this.preferenceStore.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun autoUpdateTrack() = this.preferenceStore.getBoolean(Keys.autoUpdateTrack, true)

    fun lastVersionCode() = this.preferenceStore.getInt("last_version_code", 0)

    fun trackUsername(sync: TrackService) = this.preferenceStore.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = this.preferenceStore.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = this.preferenceStore.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = this.preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = this.preferenceStore.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = this.preferenceStore.getInt("relative_time", 7)

    fun dateFormat(format: String = this.preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = this.preferenceStore.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = this.preferenceStore.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun saveChaptersAsCBZ() = this.preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = this.preferenceStore.getBoolean("split_tall_images", false)

    fun folderPerManga() = this.preferenceStore.getBoolean(Keys.folderPerManga, false)

    fun numberOfBackups() = this.preferenceStore.getInt("backup_slots", 2)

    fun backupInterval() = this.preferenceStore.getInt("backup_interval", 12)

    fun removeAfterReadSlots() = this.preferenceStore.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = this.preferenceStore.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = this.preferenceStore.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = this.preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun downloadedOnly() = this.preferenceStore.getBoolean("pref_downloaded_only", false)

    fun automaticExtUpdates() = this.preferenceStore.getBoolean("automatic_ext_updates", true)

    fun lastAppCheck() = this.preferenceStore.getLong("last_app_check", 0)
    fun lastExtCheck() = this.preferenceStore.getLong("last_ext_check", 0)

    fun downloadNewChapters() = this.preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = this.preferenceStore.getStringSet("download_new_categories", emptySet())
    fun downloadNewChapterCategoriesExclude() = this.preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = this.preferenceStore.getInt("auto_download_while_reading", 0)

    fun skipRead() = this.preferenceStore.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = this.preferenceStore.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = this.preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun filterChapterByRead() = this.preferenceStore.getInt(Keys.defaultChapterFilterByRead, DomainManga.SHOW_ALL.toInt())

    fun filterChapterByDownloaded() = this.preferenceStore.getInt(Keys.defaultChapterFilterByDownloaded, DomainManga.SHOW_ALL.toInt())

    fun filterChapterByBookmarked() = this.preferenceStore.getInt(Keys.defaultChapterFilterByBookmarked, DomainManga.SHOW_ALL.toInt())

    fun sortChapterBySourceOrNumber() = this.preferenceStore.getInt(Keys.defaultChapterSortBySourceOrNumber, DomainManga.CHAPTER_SORTING_SOURCE.toInt())

    fun displayChapterByNameOrNumber() = this.preferenceStore.getInt(Keys.defaultChapterDisplayByNameOrNumber, DomainManga.CHAPTER_DISPLAY_NAME.toInt())

    fun sortChapterByAscendingOrDescending() = this.preferenceStore.getInt(Keys.defaultChapterSortByAscendingOrDescending, DomainManga.CHAPTER_SORT_DESC.toInt())

    fun incognitoMode() = this.preferenceStore.getBoolean("incognito_mode", false)

    fun tabletUiMode() = this.preferenceStore.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = this.preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun autoClearChapterCache() = this.preferenceStore.getBoolean(Keys.autoClearChapterCache, false)

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.readFilter)
        filterChapterByDownloaded().set(manga.downloadedFilter)
        filterChapterByBookmarked().set(manga.bookmarkedFilter)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(if (manga.sortDescending()) DomainManga.CHAPTER_SORT_DESC.toInt() else DomainManga.CHAPTER_SORT_ASC.toInt())
    }
    // SY -->

    fun defaultMangaOrder() = this.preferenceStore.getString("default_manga_order", "")

    fun migrationSources() = this.preferenceStore.getString("migrate_sources", "")

    fun smartMigration() = this.preferenceStore.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = this.preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = this.preferenceStore.getBoolean("skip_pre_migration", false)

    fun hideNotFoundMigration() = this.preferenceStore.getBoolean("hide_not_found_migration", false)

    fun isHentaiEnabled() = this.preferenceStore.getBoolean("eh_is_hentai_enabled", true)

    fun enableExhentai() = this.preferenceStore.getBoolean("enable_exhentai", false)

    fun imageQuality() = this.preferenceStore.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = this.preferenceStore.getInt("eh_enable_hah", 0)

    fun useJapaneseTitle() = this.preferenceStore.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = this.preferenceStore.getBoolean("eh_useOrigImages", false)

    fun ehTagFilterValue() = this.preferenceStore.getInt("eh_tag_filtering_value", 0)

    fun ehTagWatchingValue() = this.preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    fun memberIdVal() = this.preferenceStore.getString("eh_ipb_member_id", "")

    fun passHashVal() = this.preferenceStore.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = this.preferenceStore.getString("eh_igneous", "")
    fun ehSettingsProfile() = this.preferenceStore.getInt("eh_ehSettingsProfile", -1)
    fun exhSettingsProfile() = this.preferenceStore.getInt("eh_exhSettingsProfile", -1)
    fun exhSettingsKey() = this.preferenceStore.getString("eh_settingsKey", "")
    fun exhSessionCookie() = this.preferenceStore.getString("eh_sessionCookie", "")
    fun exhHathPerksCookies() = this.preferenceStore.getString("eh_hathPerksCookie", "")

    fun exhShowSyncIntro() = this.preferenceStore.getBoolean("eh_show_sync_intro", true)

    fun exhReadOnlySync() = this.preferenceStore.getBoolean("eh_sync_read_only", false)

    fun exhLenientSync() = this.preferenceStore.getBoolean("eh_lenient_sync", false)

    fun exhShowSettingsUploadWarning() = this.preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    fun expandFilters() = this.preferenceStore.getBoolean("eh_expand_filters", false)

    fun readerThreads() = this.preferenceStore.getInt("eh_reader_threads", 2)

    fun readerInstantRetry() = this.preferenceStore.getBoolean("eh_reader_instant_retry", true)

    fun autoscrollInterval() = this.preferenceStore.getFloat("eh_util_autoscroll_interval", 3f)

    fun cacheSize() = this.preferenceStore.getString("eh_cache_size", "75")

    fun preserveReadingPosition() = this.preferenceStore.getBoolean("eh_preserve_reading_position", false)

    fun autoSolveCaptcha() = this.preferenceStore.getBoolean("eh_autosolve_captchas", false)

    fun ehLastVersionCode() = this.preferenceStore.getInt("eh_last_version_code", 0)

    fun logLevel() = this.preferenceStore.getInt(Keys.eh_logLevel, 0)

    fun exhAutoUpdateFrequency() = this.preferenceStore.getInt("eh_auto_update_frequency", 1)

    fun exhAutoUpdateRequirements() = this.preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    fun exhAutoUpdateStats() = this.preferenceStore.getString("eh_auto_update_stats", "")

    fun aggressivePageLoading() = this.preferenceStore.getBoolean("eh_aggressive_page_loading", false)

    fun preloadSize() = this.preferenceStore.getInt("eh_preload_size", 10)

    fun useAutoWebtoon() = this.preferenceStore.getBoolean("eh_use_auto_webtoon", true)

    fun exhWatchedListDefaultState() = this.preferenceStore.getBoolean("eh_watched_list_default_state", false)

    fun exhSettingsLanguages() = this.preferenceStore.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false",
    )

    fun exhEnabledCategories() = this.preferenceStore.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false",
    )

    fun feedTabInFront() = this.preferenceStore.getBoolean("latest_tab_position", false)

    fun sourceSorting() = this.preferenceStore.getInt("sources_sort", 0)

    fun recommendsInOverflow() = this.preferenceStore.getBoolean("recommends_in_overflow", false)

    fun mergeInOverflow() = this.preferenceStore.getBoolean("merge_in_overflow", false)

    fun enhancedEHentaiView() = this.preferenceStore.getBoolean("enhanced_e_hentai_view", true)

    fun webtoonEnableZoomOut() = this.preferenceStore.getBoolean("webtoon_enable_zoom_out", false)

    fun continuousVerticalTappingByPage() = this.preferenceStore.getBoolean("continuous_vertical_tapping_by_page", false)

    fun useNewSourceNavigation() = this.preferenceStore.getBoolean("use_new_source_navigation", true)

    fun preferredMangaDexId() = this.preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = this.preferenceStore.getStringSet("pref_mangadex_sync_to_library_indexes", emptySet())

    fun allowLocalSourceHiddenFolders() = this.preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun extensionRepos() = this.preferenceStore.getStringSet("extension_repos", emptySet())

    fun cropBordersContinuousVertical() = this.preferenceStore.getBoolean("crop_borders_continues_vertical", false)

    fun forceHorizontalSeekbar() = this.preferenceStore.getBoolean("pref_force_horz_seekbar", false)

    fun landscapeVerticalSeekbar() = this.preferenceStore.getBoolean("pref_show_vert_seekbar_landscape", false)

    fun leftVerticalSeekbar() = this.preferenceStore.getBoolean("pref_left_handed_vertical_seekbar", false)

    fun readerBottomButtons() = this.preferenceStore.getStringSet("reader_bottom_buttons", ReaderBottomButton.BUTTONS_DEFAULTS)

    fun bottomBarLabels() = this.preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    fun showNavUpdates() = this.preferenceStore.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = this.preferenceStore.getBoolean("pref_show_history_button", true)

    fun pageLayout() = this.preferenceStore.getInt("page_layout", PagerConfig.PageLayout.AUTOMATIC)

    fun centerMarginType() = this.preferenceStore.getInt("center_margin_type", PagerConfig.CenterMarginType.NONE)

    fun invertDoublePages() = this.preferenceStore.getBoolean("invert_double_pages", false)
}
