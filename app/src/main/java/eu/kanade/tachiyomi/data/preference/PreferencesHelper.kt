package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.database.models.Manga
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

    fun confirmExit() = preferenceStore.getBoolean("pref_confirm_exit", false)

    fun sideNavIconAlignment() = preferenceStore.getInt("pref_side_nav_icon_alignment", 0)

    fun themeMode() = preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light },
    )

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun lastVersionCode() = preferenceStore.getInt("last_version_code", 0)

    fun backupsDirectory() = preferenceStore.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = preferenceStore.getInt("relative_time", 7)

    fun dateFormat(format: String = preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = preferenceStore.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean("pref_download_only_over_wifi_key", true)

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", false)

    fun numberOfBackups() = preferenceStore.getInt("backup_slots", 2)

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean("pref_remove_after_marked_as_read_key", false)

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun downloadedOnly() = preferenceStore.getBoolean("pref_downloaded_only", false)

    fun automaticExtUpdates() = preferenceStore.getBoolean("automatic_ext_updates", true)

    fun lastAppCheck() = preferenceStore.getLong("last_app_check", 0)
    fun lastExtCheck() = preferenceStore.getLong("last_ext_check", 0)

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet("download_new_categories", emptySet())
    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun filterChapterByRead() = preferenceStore.getInt("default_chapter_filter_by_read", DomainManga.SHOW_ALL.toInt())

    fun filterChapterByDownloaded() = preferenceStore.getInt("default_chapter_filter_by_downloaded", DomainManga.SHOW_ALL.toInt())

    fun filterChapterByBookmarked() = preferenceStore.getInt("default_chapter_filter_by_bookmarked", DomainManga.SHOW_ALL.toInt())

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getInt("default_chapter_sort_by_source_or_number", DomainManga.CHAPTER_SORTING_SOURCE.toInt())

    fun displayChapterByNameOrNumber() = preferenceStore.getInt("default_chapter_display_by_name_or_number", DomainManga.CHAPTER_DISPLAY_NAME.toInt())

    fun sortChapterByAscendingOrDescending() = preferenceStore.getInt("default_chapter_sort_by_ascending_or_descending", DomainManga.CHAPTER_SORT_DESC.toInt())

    fun incognitoMode() = preferenceStore.getBoolean("incognito_mode", false)

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.readFilter)
        filterChapterByDownloaded().set(manga.downloadedFilter)
        filterChapterByBookmarked().set(manga.bookmarkedFilter)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(if (manga.sortDescending()) DomainManga.CHAPTER_SORT_DESC.toInt() else DomainManga.CHAPTER_SORT_ASC.toInt())
    }
    // SY -->

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    fun smartMigration() = preferenceStore.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean("skip_pre_migration", false)

    fun hideNotFoundMigration() = preferenceStore.getBoolean("hide_not_found_migration", false)

    fun isHentaiEnabled() = preferenceStore.getBoolean("eh_is_hentai_enabled", true)

    fun enableExhentai() = preferenceStore.getBoolean("enable_exhentai", false)

    fun imageQuality() = preferenceStore.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = preferenceStore.getInt("eh_enable_hah", 0)

    fun useJapaneseTitle() = preferenceStore.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = preferenceStore.getBoolean("eh_useOrigImages", false)

    fun ehTagFilterValue() = preferenceStore.getInt("eh_tag_filtering_value", 0)

    fun ehTagWatchingValue() = preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    fun memberIdVal() = preferenceStore.getString("eh_ipb_member_id", "")

    fun passHashVal() = preferenceStore.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = preferenceStore.getString("eh_igneous", "")
    fun ehSettingsProfile() = preferenceStore.getInt("eh_ehSettingsProfile", -1)
    fun exhSettingsProfile() = preferenceStore.getInt("eh_exhSettingsProfile", -1)
    fun exhSettingsKey() = preferenceStore.getString("eh_settingsKey", "")
    fun exhSessionCookie() = preferenceStore.getString("eh_sessionCookie", "")
    fun exhHathPerksCookies() = preferenceStore.getString("eh_hathPerksCookie", "")

    fun exhShowSyncIntro() = preferenceStore.getBoolean("eh_show_sync_intro", true)

    fun exhReadOnlySync() = preferenceStore.getBoolean("eh_sync_read_only", false)

    fun exhLenientSync() = preferenceStore.getBoolean("eh_lenient_sync", false)

    fun exhShowSettingsUploadWarning() = preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    fun expandFilters() = preferenceStore.getBoolean("eh_expand_filters", false)

    fun autoSolveCaptcha() = preferenceStore.getBoolean("eh_autosolve_captchas", false)

    fun ehLastVersionCode() = preferenceStore.getInt("eh_last_version_code", 0)

    fun logLevel() = preferenceStore.getInt("eh_log_level", 0)

    fun exhAutoUpdateFrequency() = preferenceStore.getInt("eh_auto_update_frequency", 1)

    fun exhAutoUpdateRequirements() = preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    fun exhAutoUpdateStats() = preferenceStore.getString("eh_auto_update_stats", "")

    fun exhWatchedListDefaultState() = preferenceStore.getBoolean("eh_watched_list_default_state", false)

    fun exhSettingsLanguages() = preferenceStore.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false",
    )

    fun exhEnabledCategories() = preferenceStore.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false",
    )

    fun feedTabInFront() = preferenceStore.getBoolean("latest_tab_position", false)

    fun sourceSorting() = preferenceStore.getInt("sources_sort", 0)

    fun recommendsInOverflow() = preferenceStore.getBoolean("recommends_in_overflow", false)

    fun mergeInOverflow() = preferenceStore.getBoolean("merge_in_overflow", false)

    fun enhancedEHentaiView() = preferenceStore.getBoolean("enhanced_e_hentai_view", true)

    fun useNewSourceNavigation() = preferenceStore.getBoolean("use_new_source_navigation", true)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet("pref_mangadex_sync_to_library_indexes", emptySet())

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun bottomBarLabels() = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    fun showNavUpdates() = preferenceStore.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = preferenceStore.getBoolean("pref_show_history_button", true)
}
