package exh.source

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.service.AppUpdatePolicy

class ExhPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // KMK -->
    fun appShouldAutoUpdate() = preferenceStore.getStringSet(
        "should_auto_update",
        setOf(
            AppUpdatePolicy.DEVICE_ONLY_ON_WIFI,
        ),
    )
    // KMK <--

    // SY -->
    fun isHentaiEnabled() = preferenceStore.getBoolean("eh_is_hentai_enabled", false)

    // KMK -->
    fun ehIncognitoMode() = preferenceStore.getBoolean("eh_incognito_mode", false)
    // KMK <--

    fun enableExhentai() = preferenceStore.getBoolean(Preference.Companion.privateKey("enable_exhentai"), false)

    fun imageQuality() = preferenceStore.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = preferenceStore.getInt("eh_enable_hah", 0)

    fun useJapaneseTitle() = preferenceStore.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = preferenceStore.getBoolean("eh_useOrigImages", false)

    fun ehTagFilterValue() = preferenceStore.getInt("eh_tag_filtering_value", 0)

    fun ehTagWatchingValue() = preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    fun memberIdVal() = preferenceStore.getString(Preference.Companion.privateKey("eh_ipb_member_id"), "")

    fun passHashVal() = preferenceStore.getString(Preference.Companion.privateKey("eh_ipb_pass_hash"), "")
    fun igneousVal() = preferenceStore.getString(Preference.Companion.privateKey("eh_igneous"), "")
    fun ehSettingsProfile() = preferenceStore.getInt(Preference.Companion.privateKey("eh_ehSettingsProfile"), -1)
    fun exhSettingsProfile() = preferenceStore.getInt(Preference.Companion.privateKey("eh_exhSettingsProfile"), -1)
    fun exhSettingsKey() = preferenceStore.getString(Preference.Companion.privateKey("eh_settingsKey"), "")
    fun exhSessionCookie() = preferenceStore.getString(Preference.Companion.privateKey("eh_sessionCookie"), "")
    fun exhHathPerksCookies() = preferenceStore.getString(Preference.Companion.privateKey("eh_hathPerksCookie"), "")

    fun exhShowSyncIntro() = preferenceStore.getBoolean("eh_show_sync_intro", true)

    fun exhReadOnlySync() = preferenceStore.getBoolean("eh_sync_read_only", false)

    fun exhLenientSync() = preferenceStore.getBoolean("eh_lenient_sync", false)

    fun exhShowSettingsUploadWarning() = preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    fun logLevel() = preferenceStore.getInt("eh_log_level", 0)

    fun exhAutoUpdateFrequency() = preferenceStore.getInt("eh_auto_update_frequency", 1)

    fun exhAutoUpdateRequirements() = preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    fun exhAutoUpdateStats() = preferenceStore.getString(Preference.Companion.appStateKey("eh_auto_update_stats"), "")

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
}
