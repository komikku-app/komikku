package tachiyomi.domain

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UnsortedPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // SY -->

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    fun smartMigration() = preferenceStore.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean(Preference.appStateKey("skip_pre_migration"), false)

    fun hideNotFoundMigration() = preferenceStore.getBoolean("hide_not_found_migration", false)

    fun showOnlyUpdatesMigration() = preferenceStore.getBoolean("show_only_updates_migration", false)

    fun isHentaiEnabled() = preferenceStore.getBoolean("eh_is_hentai_enabled", true)

    fun enableExhentai() = preferenceStore.getBoolean(Preference.privateKey("enable_exhentai"), false)

    fun imageQuality() = preferenceStore.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = preferenceStore.getInt("eh_enable_hah", 0)

    fun useJapaneseTitle() = preferenceStore.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = preferenceStore.getBoolean("eh_useOrigImages", false)

    fun ehTagFilterValue() = preferenceStore.getInt("eh_tag_filtering_value", 0)

    fun ehTagWatchingValue() = preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    fun memberIdVal() = preferenceStore.getString(Preference.privateKey("eh_ipb_member_id"), "")

    fun passHashVal() = preferenceStore.getString(Preference.privateKey("eh_ipb_pass_hash"), "")
    fun igneousVal() = preferenceStore.getString(Preference.privateKey("eh_igneous"), "")
    fun ehSettingsProfile() = preferenceStore.getInt(Preference.privateKey("eh_ehSettingsProfile"), -1)
    fun exhSettingsProfile() = preferenceStore.getInt(Preference.privateKey("eh_exhSettingsProfile"), -1)
    fun exhSettingsKey() = preferenceStore.getString(Preference.privateKey("eh_settingsKey"), "")
    fun exhSessionCookie() = preferenceStore.getString(Preference.privateKey("eh_sessionCookie"), "")
    fun exhHathPerksCookies() = preferenceStore.getString(Preference.privateKey("eh_hathPerksCookie"), "")

    fun exhShowSyncIntro() = preferenceStore.getBoolean("eh_show_sync_intro", true)

    fun exhReadOnlySync() = preferenceStore.getBoolean("eh_sync_read_only", false)

    fun exhLenientSync() = preferenceStore.getBoolean("eh_lenient_sync", false)

    fun exhShowSettingsUploadWarning() = preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    fun logLevel() = preferenceStore.getInt("eh_log_level", 0)

    fun exhAutoUpdateFrequency() = preferenceStore.getInt("eh_auto_update_frequency", 1)

    fun exhAutoUpdateRequirements() = preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    fun exhAutoUpdateStats() = preferenceStore.getString(Preference.appStateKey("eh_auto_update_stats"), "")

    fun exhWatchedListDefaultState() = preferenceStore.getBoolean("eh_watched_list_default_state", false)

    fun exhSettingsLanguages() = preferenceStore.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false",
    )

    fun exhEnabledCategories() = preferenceStore.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false",
    )

    fun enhancedEHentaiView() = preferenceStore.getBoolean("enhanced_e_hentai_view", true)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)
}
