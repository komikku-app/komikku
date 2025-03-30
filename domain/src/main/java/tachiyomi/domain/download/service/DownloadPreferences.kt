package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveEpisodesAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsSeen() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(
        "remove_exclude_categories",
        emptySet(),
    )

    fun downloadNewEpisodes() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewEpisodeCategories() = preferenceStore.getStringSet(
        "download_new_categories",
        emptySet(),
    )

    fun downloadNewEpisodeCategoriesExclude() = preferenceStore.getStringSet(
        "download_new_categories_exclude",
        emptySet(),
    )

    fun downloadNewUnseenEpisodesOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)
    // KMK <--
}
